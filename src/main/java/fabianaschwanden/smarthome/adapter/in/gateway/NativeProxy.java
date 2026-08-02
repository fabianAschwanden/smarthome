package fabianaschwanden.smarthome.adapter.in.gateway;

import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
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
import java.util.Set;

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

    /**
     * Header, die ans Gerät weitergereicht werden – bewusst eine Allowlist: Geräte im LAN
     * sprechen unverschlüsseltes HTTP und sind nicht vertrauenswürdig. Alles andere bleibt
     * hier, insbesondere {@code Cookie}/{@code Authorization} (sonst erntet ein loggendes
     * oder kompromittiertes Gerät gültige App-Sessions) und {@code X-Forwarded-*}.
     */
    private static final Set<String> FORWARDED_REQUEST_HEADERS = Set.of(
            "accept", "accept-language", "accept-charset", "content-type", "content-length",
            "cache-control", "pragma", "range", "if-modified-since", "if-none-match", "if-range",
            "user-agent");

    /**
     * Endungen, die der Referer-Fallback durchlässt. Der Fallback existiert nur für die
     * ABSOLUTEN Asset-/AJAX-Pfade der Fremd-UI (z. B. {@code /values.xml}) – relative Pfade
     * laufen dank {@code <base>} ohnehin über {@code /native/<id>/} und sind unbeschränkt.
     * Ohne diese Liste wäre der Fallback ein offener Proxy auf das Gerät: ein gefälschter
     * {@code Referer} genügte für {@code GET /setswrel.cgi?rel=1&state=1} am SMARTFOX,
     * vorbei an der Fachregel {@code ManualSwitchNotAllowed}.
     */
    private static final Set<String> FALLBACK_EXTENSIONS = Set.of(
            "xml", "json", "css", "js", "txt", "html", "htm", "shtml",
            "png", "jpg", "jpeg", "gif", "svg", "ico", "webp", "woff", "woff2", "ttf", "eot");

    private final Vertx vertx;
    private final Config appConfig;
    private final Map<String, URI> targets = new HashMap<>();
    private HttpClient client;
    private String contentSecurityPolicy;

    public NativeProxy(Vertx vertx, Config appConfig) {
        this.vertx = vertx;
        this.appConfig = appConfig;
    }

    /**
     * Baut die CSP für Proxy-Antworten. Quellen bleiben grosszügig (Fremd-UIs nutzen
     * Inline-Skripte/-Styles), aber die ZIELE sind auf die eigene Origin begrenzt: eine
     * kompromittierte Firmware kann so keine Daten an einen fremden Host schicken – weder
     * per fetch/XHR ({@code connect-src}) noch per {@code <img src="http://…">}.
     *
     * <p>{@code smarthome.nativeview-style-src-extra} ist das Ventil für Geräte-UIs, die ihr
     * Stylesheet beim Hersteller laden (der SMARTFOX holt sein einziges CSS von
     * {@code my.smartfox.at} und bringt keine eigenen {@code <style>}-Blöcke mit – ohne
     * die Ausnahme rendert die View unformatiert). Bewusst nur {@code style-src} und
     * bewusst per Config statt hartkodiert: der gerätespezifische Host gehört dorthin, wo
     * auch alle anderen Geräte-Fakten stehen (gitignorte {@code config/}). Der Schlüssel
     * liegt unter {@code smarthome.*} und nicht unter {@code nativeview.*}, weil letzteres
     * der @ConfigMapping NativeViewConfig gehört, die fremde Unterschlüssel abweist.
     */
    private String buildCsp() {
        String extraStyle = appConfig.getOptionalValue("smarthome.nativeview-style-src-extra", String.class)
                .filter(s -> !s.isBlank())
                .map(s -> " " + s.trim())
                .orElse("");
        return "default-src 'self' 'unsafe-inline' 'unsafe-eval' data: blob:; "
                + "style-src 'self' 'unsafe-inline' data:" + extraStyle + "; "
                + "connect-src 'self'; form-action 'self'; base-uri 'self'; "
                + "frame-ancestors 'self'; object-src 'none'";
    }

    void init(@Observes StartupEvent ev) {
        client = vertx.createHttpClient(new HttpClientOptions().setIdleTimeout(0));
        contentSecurityPolicy = buildCsp();
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
        String uri = ctx.request().uri();
        if (isAppPath(uri)) {
            ctx.next(); // echte App-Route -> niemals kapern
            return;
        }
        String id = refererTargetId(ctx.request().getHeader("Referer"));
        if (id == null || isCrossSiteRequest(ctx)) {
            ctx.next(); // kein Native-Kontext -> normale App-Route weitermachen
            return;
        }
        // Der Fallback hängt an einem frei setzbaren Header und ist deshalb eng gefasst:
        // nur Lesezugriffe auf statische Asset-/Daten-Pfade. Alles andere gehört über den
        // direkten Pfad /native/<id>/... (der die Fremd-UI unbeschränkt bedient).
        HttpMethod method = ctx.request().method();
        if (!HttpMethod.GET.equals(method) && !HttpMethod.HEAD.equals(method)) {
            ctx.next();
            return;
        }
        String path = pathOf(uri);
        if (!hasAllowedExtension(path) || isTraversal(path)) {
            ctx.next();
            return;
        }
        proxyTo(ctx, id, uri);
    }

    /** Pfadanteil ohne Query-String. */
    private static String pathOf(String uri) {
        int q = uri.indexOf('?');
        return q < 0 ? uri : uri.substring(0, q);
    }

    private static boolean hasAllowedExtension(String path) {
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        if (dot < 0 || dot < slash) {
            return false; // kein Dateiname mit Endung -> kein Asset
        }
        return FALLBACK_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase());
    }

    /**
     * Lehnt Pfade ab, die sich beim Normalisieren verändern – {@code ..}-Segmente gehen
     * sonst unverändert ans Gerät und können dort auf andere Endpunkte auflösen.
     */
    private static boolean isTraversal(String path) {
        try {
            String normalized = URI.create(path).normalize().getPath();
            return normalized == null || !normalized.equals(path);
        } catch (IllegalArgumentException e) {
            return true; // nicht parsbar -> nicht weiterreichen
        }
    }

    /**
     * Liefert die Native-id, wenn der Referer-PFAD mit {@code /native/<id>/} beginnt.
     * Bewusst über {@link URI} statt {@code indexOf}: sonst genügte ein beliebiger String
     * mit {@code /native/smartfox/} irgendwo darin (auch im Query oder Fragment).
     */
    private String refererTargetId(String referer) {
        if (referer == null) {
            return null;
        }
        String path;
        try {
            path = URI.create(referer).getPath();
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (path == null || !path.startsWith(PREFIX)) {
            return null;
        }
        String rest = path.substring(PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return null; // /native/<id> ohne abschliessenden Slash ist kein UI-Kontext
        }
        String id = rest.substring(0, slash);
        return targets.containsKey(id) ? id : null;
    }

    /**
     * CSRF-Schutz. Die Fremd-UIs schalten teils per GET ({@code /setswrel.cgi?rel=1&state=1}
     * am SMARTFOX), und SameSite=Lax schickt das Session-Cookie bei Top-Level-GET-Navigation
     * mit – ein präparierter Link genügte also für einen Schaltbefehl.
     * {@code Sec-Fetch-Site: cross-site} kennzeichnet genau diesen Fall; Aufrufe aus dem
     * eingebetteten iframe sind {@code same-origin}, ein direkt eingetipptes Lesezeichen
     * {@code none}. Ältere Browser ohne den Header laufen durch – zusätzliche Schranke,
     * keine alleinige.
     *
     * <p>Bewusst hier statt per SameSite=strict: strict bricht den OIDC-Code-Flow
     * (State-Cookie fehlt beim Rücksprung vom IdP, quarkusio/quarkus#30625).
     */
    private static boolean isCrossSiteRequest(RoutingContext ctx) {
        return "cross-site".equalsIgnoreCase(ctx.request().getHeader("Sec-Fetch-Site"));
    }

    private void handle(RoutingContext ctx) {
        if (isCrossSiteRequest(ctx)) {
            LOG.debugf("native-Proxy: Cross-Site-Zugriff auf %s abgewiesen", ctx.request().uri());
            ctx.response().setStatusCode(403).end("Cross-Site-Zugriff auf native Views nicht erlaubt");
            return;
        }
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
                        if (FORWARDED_REQUEST_HEADERS.contains(h.getKey().toLowerCase())) {
                            req.putHeader(h.getKey(), h.getValue());
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
            // Set-Cookie des Geräts NIE durchreichen: die Antwort kommt auf der App-Origin
            // an, das Gerät könnte damit Cookies in die App-Domain injizieren.
            if (k.equalsIgnoreCase("Set-Cookie") || k.equalsIgnoreCase("Set-Cookie2")) {
                return;
            }
            // Content-Length setzen wir unten selbst (Body wird gepuffert; bei HTML ändert
            // die <base>-Injektion ohnehin die Länge). Original-Header hier auslassen.
            if (k.equalsIgnoreCase("Content-Length") || k.equalsIgnoreCase("Transfer-Encoding")) {
                return;
            }
            ctx.response().putHeader(k, h.getValue());
        });

        // Eigene CSP statt der entfernten des Geräts (siehe buildCsp()).
        ctx.response().putHeader("Content-Security-Policy", contentSecurityPolicy);
        ctx.response().putHeader("X-Content-Type-Options", "nosniff");

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
