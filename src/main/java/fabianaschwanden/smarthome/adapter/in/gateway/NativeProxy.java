package fabianaschwanden.smarthome.adapter.in.gateway;

import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reverse-Proxy für native Geräte-Weboberflächen: reicht {@code /native/<id>/*} an die
 * unter {@code nativeview.targets[i].url} hinterlegte Geräte-URL weiter (z. B. SMARTFOX auf
 * {@code http://192.168.1.124}).
 *
 * <p>Damit läuft die fremde UI über DIESELBE Origin/denselben Port wie die App (8080) –
 * remote durch den Fly-Tunnel erreichbar und ohne Mixed-Content-Block (HTTPS-Seite,
 * HTTP-Gerät). Zwei Eingriffe machen die Einbettung im iframe robust:
 * <ul>
 *   <li>{@code X-Frame-Options}/{@code Content-Security-Policy:frame-ancestors} werden aus
 *       der Antwort entfernt – sonst verweigert der Browser das iframe.</li>
 *   <li>In HTML-Antworten wird ein {@code <base href="/native/<id>/">} injiziert, damit
 *       relative Pfade des Geräts korrekt durch den Proxy aufgelöst werden.</li>
 * </ul>
 * Die Geräte-URL bleibt serverseitig; das Frontend kennt nur den Proxy-Pfad. Die
 * {@code native.targets}-Properties werden direkt über die Config gelesen (nicht über die
 * @ConfigMapping-Bean des out-Adapters – Adapter referenzieren einander nicht, §3.4).
 */
@ApplicationScoped
public class NativeProxy {

    private static final Logger LOG = Logger.getLogger(NativeProxy.class);
    private static final String PREFIX = "/native/";

    private final Vertx vertx;
    private final Config appConfig;
    private final Map<String, URI> targets = new HashMap<>();
    private HttpClient client;

    public NativeProxy(Vertx vertx, Config appConfig) {
        this.vertx = vertx;
        this.appConfig = appConfig;
    }

    void init(@Observes StartupEvent ev) {
        client = vertx.createHttpClient(new HttpClientOptions().setIdleTimeout(0));
        // nativeview.targets[i].id / .url so lange lesen, bis kein weiterer Eintrag existiert.
        for (int i = 0; ; i++) {
            Optional<String> id = appConfig.getOptionalValue("nativeview.targets[" + i + "].id", String.class);
            if (id.isEmpty()) {
                break;
            }
            appConfig.getOptionalValue("nativeview.targets[" + i + "].url", String.class)
                    .ifPresent(url -> targets.put(id.get(), URI.create(url)));
        }
    }

    public void routes(@Observes Router router) {
        router.route(PREFIX + "*").handler(this::handle);
    }

    private void handle(RoutingContext ctx) {
        String rest = ctx.request().uri().substring(PREFIX.length()); // "<id>/<pfad...>"
        int slash = rest.indexOf('/');
        String id = slash < 0 ? rest : rest.substring(0, slash);
        String path = slash < 0 ? "/" : rest.substring(slash);
        if (path.isEmpty()) {
            path = "/";
        }

        URI target = targets.get(id);
        if (target == null) {
            ctx.response().setStatusCode(404).end("Unbekannte native View: " + id);
            return;
        }

        boolean https = "https".equalsIgnoreCase(target.getScheme());
        int port = target.getPort() != -1 ? target.getPort() : (https ? 443 : 80);
        RequestOptions opts = new RequestOptions()
                .setMethod(ctx.request().method())
                .setHost(target.getHost())
                .setPort(port)
                .setSsl(https)
                .setURI(path);

        client.request(opts)
                .onSuccess(req -> {
                    ctx.request().headers().forEach(h -> {
                        String k = h.getKey();
                        if (!k.equalsIgnoreCase("Host") && !k.equalsIgnoreCase("Accept-Encoding")) {
                            req.putHeader(k, h.getValue());
                        }
                    });
                    req.response().onSuccess(resp -> relayResponse(ctx, resp, id)).onFailure(err -> fail(ctx, err, id));
                    ctx.request().pipeTo(req);
                })
                .onFailure(err -> fail(ctx, err, id));
    }

    private void relayResponse(RoutingContext ctx, io.vertx.core.http.HttpClientResponse resp, String id) {
        String contentType = resp.getHeader("Content-Type");
        boolean isHtml = contentType != null && contentType.toLowerCase().contains("text/html");

        ctx.response().setStatusCode(resp.statusCode());
        resp.headers().forEach(h -> {
            String k = h.getKey();
            // Frame-Blocker entfernen, sonst lehnt der Browser das iframe ab.
            if (k.equalsIgnoreCase("X-Frame-Options") || k.equalsIgnoreCase("Content-Security-Policy")) {
                return;
            }
            // Bei HTML stimmt die Länge nach der <base>-Injektion nicht mehr -> weglassen.
            if (isHtml && k.equalsIgnoreCase("Content-Length")) {
                return;
            }
            ctx.response().putHeader(k, h.getValue());
        });

        if (isHtml) {
            // HTML puffern, <base> injizieren, dann senden.
            resp.body().onSuccess(body -> {
                String html = body.toString(StandardCharsets.UTF_8);
                String base = "<base href=\"" + PREFIX + id + "/\">";
                String patched = injectBase(html, base);
                ctx.response().end(Buffer.buffer(patched, "UTF-8"));
            }).onFailure(err -> fail(ctx, err, id));
        } else {
            // Assets (CSS/JS/Bilder/XML) durchstreamen (Backpressure via Pipe).
            resp.pipeTo(ctx.response());
        }
    }

    /** Fügt den {@code <base>}-Tag direkt nach {@code <head>} ein (sonst am Anfang). */
    private static String injectBase(String html, String base) {
        int head = indexOfIgnoreCase(html, "<head>");
        if (head >= 0) {
            int at = head + "<head>".length();
            return html.substring(0, at) + base + html.substring(at);
        }
        return base + html;
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().indexOf(needle.toLowerCase());
    }

    private void fail(RoutingContext ctx, Throwable err, String id) {
        LOG.debugf(err, "native-Proxy: Ziel '%s' nicht erreichbar", id);
        if (!ctx.response().ended()) {
            ctx.response().setStatusCode(502).end("Native View '" + id + "' nicht erreichbar");
        }
    }
}
