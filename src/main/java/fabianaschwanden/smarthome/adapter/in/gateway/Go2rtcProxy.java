package fabianaschwanden.smarthome.adapter.in.gateway;

import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

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
 */
@ApplicationScoped
public class Go2rtcProxy {

    private static final Logger LOG = Logger.getLogger(Go2rtcProxy.class);
    private static final String PREFIX = "/go2rtc";

    private final Vertx vertx;
    private final String upstreamHost;
    private final int upstreamPort;
    private HttpClient client;
    private WebSocketClient wsClient;

    public Go2rtcProxy(
            Vertx vertx,
            @ConfigProperty(name = "camera.go2rtc.host", defaultValue = "localhost") String host,
            @ConfigProperty(name = "camera.go2rtc.port", defaultValue = "1984") int port) {
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
        // WebSocket-Upgrade (go2rtc-MSE-Player /api/ws) gesondert bridgen.
        if ("websocket".equalsIgnoreCase(ctx.request().getHeader("Upgrade"))) {
            proxyWebSocket(ctx, target);
            return;
        }
        client.request(ctx.request().method(), target)
                .onSuccess(req -> {
                    ctx.request().headers().forEach(h -> {
                        if (!h.getKey().equalsIgnoreCase("Host")) {
                            req.putHeader(h.getKey(), h.getValue());
                        }
                    });
                    req.response().onSuccess(resp -> {
                        ctx.response().setStatusCode(resp.statusCode());
                        resp.headers().forEach(h -> ctx.response().putHeader(h.getKey(), h.getValue()));
                        resp.pipeTo(ctx.response()); // Body durchstreamen (Backpressure via Vert.x-Pipe)
                    }).onFailure(err -> fail(ctx, err));
                    ctx.request().pipeTo(req); // Request-Body weiterleiten (z. B. POST /api/webrtc)
                })
                .onFailure(err -> fail(ctx, err));
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
