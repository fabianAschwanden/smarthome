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
        // 1) Direkter Pfad /native/<id>/...
        router.route(PREFIX + "*").handler(this::handle);
        // 2) Referer-Fallback: absolute Pfade der Fremd-UI (z. B. XHR auf /values.xml)
        //    tragen kein /native/-Präfix; der <base>-Tag wirkt nur auf RELATIVE Pfade.
        //    Solche Anfragen anhand des Referers dem richtigen Gerät zuordnen. Greift NUR,
        //    wenn der Referer auf /native/<id>/ zeigt – App-Routen (/api, /go2rtc, /q,
        //    Frontend) bleiben unberührt.
        router.route().handler(this::handleByReferer);
    }

    /** Eigene App-Pfade, die NIE über den Referer-Fallback ans Gerät gehen dürfen. */
    private static boolean isAppPath(String uri) {
        return uri.startsWith("/api/") || uri.startsWith("/go2rtc/") || uri.startsWith("/q/")
                || uri.startsWith(PREFIX);
    }

    /** Fallback für absolute Pfade der Fremd-UI: Ziel aus dem Referer ableiten. */
    private void handleByReferer(RoutingContext ctx) {
        if (isAppPath(ctx.request().uri())) {
            ctx.next(); // echte App-Route -> niemals kapern
            return;
        }
        String id = refererTargetId(ctx.request().getHeader("Referer"));
        if (id == null) {
            ctx.next(); // kein Native-Kontext -> normale App-Route weitermachen
            return;
        }
        proxyTo(ctx, id, ctx.request().uri());
    }

    /** Liefert die Native-id, wenn der Referer auf /native/<id>/ zeigt – sonst null. */
    private String refererTargetId(String referer) {
        if (referer == null) {
            return null;
        }
        int at = referer.indexOf(PREFIX);
        if (at < 0) {
            return null;
        }
        String rest = referer.substring(at + PREFIX.length());
        int slash = rest.indexOf('/');
        String id = slash < 0 ? rest : rest.substring(0, slash);
        return targets.containsKey(id) ? id : null;
    }

    private void handle(RoutingContext ctx) {
        String rest = ctx.request().uri().substring(PREFIX.length()); // "<id>/<pfad...>"
        int slash = rest.indexOf('/');
        String id = slash < 0 ? rest : rest.substring(0, slash);
        String path = slash < 0 ? "/" : rest.substring(slash);
        if (path.isEmpty()) {
            path = "/";
        }
        if (!targets.containsKey(id)) {
            ctx.response().setStatusCode(404).end("Unbekannte native View: " + id);
            return;
        }
        proxyTo(ctx, id, path);
    }

    private void proxyTo(RoutingContext ctx, String id, String path) {
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
            // Content-Length setzen wir unten selbst (Body wird gepuffert; bei HTML ändert
            // die <base>-Injektion ohnehin die Länge). Original-Header hier auslassen.
            if (k.equalsIgnoreCase("Content-Length") || k.equalsIgnoreCase("Transfer-Encoding")) {
                return;
            }
            ctx.response().putHeader(k, h.getValue());
        });

        // Body immer vollständig puffern und mit end(buffer) senden – zuverlässiger als
        // pipeTo (sonst lieferten kleine Geräte-Antworten wie language_de.xml 0 Bytes,
        // weil Header/Stream-Commit kollidierten). Die Fremd-UI-Dateien sind klein.
        resp.body().onSuccess(body -> {
            if (isHtml) {
                String base = "<base href=\"" + PREFIX + id + "/\">";
                String patched = injectBase(body.toString(StandardCharsets.UTF_8), base);
                ctx.response().end(Buffer.buffer(patched, "UTF-8"));
            } else {
                ctx.response().end(body);
            }
        }).onFailure(err -> fail(ctx, err, id));
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
