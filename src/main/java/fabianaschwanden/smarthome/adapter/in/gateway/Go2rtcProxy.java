package fabianaschwanden.smarthome.adapter.in.gateway;

import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Set;

/**
 * Reverse-Proxy für das go2rtc-Stream-Gateway: reicht {@code /go2rtc/*} an den lokal
 * laufenden go2rtc-Dienst (Default {@code localhost:1984}) weiter.
 *
 * <p>So läuft der Kamera-Verkehr über DIESELBE Origin/denselben Port wie die App
 * (8080). Remote (Fly-Login-Proxy + WireGuard) ist nur dieser Port erreichbar; ein
 * separater go2rtc-Port (1984) wäre von aussen schwarz. Unterstützt sowohl den reinen
 * HTTP-Streaming-Pfad ({@code stream.mp4}) als auch den WebSocket-Pfad
 * ({@code /api/ws}) – Letzterer treibt go2rtcs MSE-Player (stream.html), der über
 * instabile Remote-Verbindungen robuster ist als ein roher progressiver MP4-Download.
 *
 * <p><b>Allowlist:</b> Weitergereicht wird ausschliesslich, was der Player braucht
 * ({@link #ALLOWED_GET} / {@link #ALLOWED_POST}); alles andere endet mit 403. go2rtcs
 * Admin-API ist damit auch dann unerreichbar, wenn die App selbst ohne Login läuft
 * ({@code %lan}): {@code /api/streams} legt sonst Stream-Quellen vom Typ {@code exec:}
 * an (Kommandoausführung im go2rtc-Container) und gibt die RTSP-URL preis, die laut
 * {@code docs/camera/SPEC.md} das Gateway nie verlassen darf. Der Vergleich läuft
 * bewusst als exakter Match auf den ROHEN Pfad – so gibt es keine Differenz zwischen
 * dem, was geprüft, und dem, was weitergereicht wird (kein Traversal, kein
 * Encoding-Trick).
 */
@ApplicationScoped
public class Go2rtcProxy {

    private static final Logger LOG = Logger.getLogger(Go2rtcProxy.class);
    private static final String PREFIX = "/go2rtc";

    /** Player-Seite, ihre beiden Skripte und die lesenden Stream-Endpunkte. */
    private static final Set<String> ALLOWED_GET = Set.of(
            "/stream.html",
            "/video-stream.js",
            "/video-rtc.js",
            "/api/ws",
            "/api/frame.jpeg",
            "/api/stream.mp4");

    /** WebRTC-Signaling (SDP-Austausch) des Players. */
    private static final Set<String> ALLOWED_POST = Set.of("/api/webrtc");

    /** WebSocket-Upgrade gibt es nur fürs MSE-Signaling. */
    private static final String ALLOWED_WEBSOCKET = "/api/ws";

    private final Vertx vertx;
    private final String upstreamHost;
    private final int upstreamPort;
    private HttpClient client;
    private WebSocketClient wsClient;

    public Go2rtcProxy(
            Vertx vertx,
            // Eigener Namespace: 'camera.*' gehoert der @ConfigMapping CameraConfig, die
            // fremde Unterschluessel als "does not map to any root" abweist.
            @ConfigProperty(name = "go2rtc.host", defaultValue = "localhost") String host,
            @ConfigProperty(name = "go2rtc.port", defaultValue = "1984") int port) {
        this.vertx = vertx;
        this.upstreamHost = host;
        this.upstreamPort = port;
    }

    void init(@Observes StartupEvent ev) {
        client = vertx.createHttpClient(new HttpClientOptions()
                .setDefaultHost(upstreamHost)
                .setDefaultPort(upstreamPort)
                // Streams (stream.mp4) sind langlebig: kein Idle-Timeout erzwingen.
                .setIdleTimeout(0));
        wsClient = vertx.createWebSocketClient();
    }

    /** Registriert die Proxy-Route. {@code @Observes Router} ist der Quarkus-Weg, Vert.x-Routen beizusteuern. */
    public void routes(@Observes Router router) {
        router.route(PREFIX + "/*").handler(this::handle);
    }

    private void handle(RoutingContext ctx) {
        String target = ctx.request().uri().substring(PREFIX.length());
        if (target.isEmpty()) {
            target = "/";
        }
        boolean upgrade = "websocket".equalsIgnoreCase(ctx.request().getHeader("Upgrade"));
        if (!isAllowed(ctx.request().method(), pathOf(target), upgrade)) {
            LOG.debugf("go2rtc-Proxy: %s %s abgewiesen (nicht auf der Allowlist)",
                    ctx.request().method(), target);
            ctx.response().setStatusCode(403).end("Pfad im go2rtc-Proxy nicht freigegeben");
            return;
        }
        // WebSocket-Upgrade (go2rtc-MSE-Player /api/ws) gesondert bridgen.
        if (upgrade) {
            proxyWebSocket(ctx, target);
            return;
        }
        client.request(ctx.request().method(), target)
                .onSuccess(req -> {
                    ctx.request().headers().forEach(h -> {
                        if (!h.getKey().equalsIgnoreCase("Host") && !isCredentialHeader(h.getKey())) {
                            req.putHeader(h.getKey(), h.getValue());
                        }
                    });
                    req.response().onSuccess(resp -> {
                        ctx.response().setStatusCode(resp.statusCode());
                        resp.headers().forEach(h -> {
                            if (!h.getKey().equalsIgnoreCase("Set-Cookie")) {
                                ctx.response().putHeader(h.getKey(), h.getValue());
                            }
                        });
                        resp.pipeTo(ctx.response()); // Body durchstreamen (Backpressure via Vert.x-Pipe)
                    }).onFailure(err -> fail(ctx, err));
                    ctx.request().pipeTo(req); // Request-Body weiterleiten (z. B. POST /api/webrtc)
                })
                .onFailure(err -> fail(ctx, err));
    }

    /**
     * Anmelde-Header gehen nicht an go2rtc: der Dienst wertet sie nicht aus, protokolliert
     * aber – und die App-Session hat im Gateway nichts zu suchen.
     */
    private static boolean isCredentialHeader(String key) {
        String k = key.toLowerCase();
        return k.equals("cookie") || k.equals("authorization") || k.equals("proxy-authorization")
                || k.startsWith("x-forwarded-") || k.startsWith("x-auth-request-") || k.equals("forwarded");
    }

    /** Pfadanteil ohne Query-String – der Query trägt nur {@code src}/{@code mode}. */
    private static String pathOf(String target) {
        int q = target.indexOf('?');
        return q < 0 ? target : target.substring(0, q);
    }

    private static boolean isAllowed(HttpMethod method, String path, boolean upgrade) {
        if (upgrade) {
            return ALLOWED_WEBSOCKET.equals(path);
        }
        if (HttpMethod.GET.equals(method)) {
            return ALLOWED_GET.contains(path);
        }
        return HttpMethod.POST.equals(method) && ALLOWED_POST.contains(path);
    }

    /** WebSocket-Bridge Browser ↔ Proxy ↔ go2rtc (für den MSE-Player /api/ws). */
    private void proxyWebSocket(RoutingContext ctx, String target) {
        WebSocketConnectOptions opts = new WebSocketConnectOptions()
                .setHost(upstreamHost).setPort(upstreamPort).setURI(target);
        wsClient.connect(opts).onSuccess(upstream ->
                ctx.request().toWebSocket().onSuccess(downstream -> {
                    // Frames in beide Richtungen weiterreichen.
                    downstream.frameHandler(upstream::writeFrame);
                    upstream.frameHandler(downstream::writeFrame);
                    downstream.closeHandler(v -> upstream.close());
                    upstream.closeHandler(v -> downstream.close());
                    downstream.exceptionHandler(t -> upstream.close());
                    upstream.exceptionHandler(t -> downstream.close());
                }).onFailure(err -> {
                    LOG.debugf(err, "go2rtc-WS: Downstream-Upgrade fehlgeschlagen");
                    upstream.close();
                })
        ).onFailure(err -> {
            LOG.debugf(err, "go2rtc-WS: Upstream nicht erreichbar (%s:%d)", upstreamHost, upstreamPort);
            if (!ctx.response().ended()) {
                ctx.response().setStatusCode(502).end("go2rtc-WebSocket nicht erreichbar");
            }
        });
    }

    private void fail(RoutingContext ctx, Throwable err) {
        LOG.debugf(err, "go2rtc-Proxy: Upstream nicht erreichbar (%s:%d)", upstreamHost, upstreamPort);
        if (!ctx.response().ended()) {
            ctx.response().setStatusCode(502).end("go2rtc nicht erreichbar");
        }
    }
}
