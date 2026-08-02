package fabianaschwanden.smarthome.adapter.in.gateway;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/**
 * Integrationstest der Allowlist des go2rtc-Proxys gegen einen lokalen Fake-go2rtc
 * (127.0.0.1:18098). Der Fake beantwortet AUCH die Admin-Pfade – wenn der Proxy hier
 * 403 liefert statt der Fake-Antwort, greift die Allowlist (und nicht etwa ein 404).
 *
 * <p>Hintergrund: go2rtcs Admin-API kann per {@code PUT /api/streams} eine Stream-Quelle
 * vom Typ {@code exec:} anlegen – das ist Kommandoausführung im go2rtc-Container – und
 * gibt über {@code GET /api/streams} die RTSP-URL preis. Im {@code %lan}-Profil läuft die
 * App ohne Login, der Proxy ist also die einzige Schranke.
 */
@QuarkusTest
@TestProfile(Go2rtcProxyTest.Profile.class)
class Go2rtcProxyTest {

    static final int FAKE_PORT = 18098;
    private static HttpServer server;

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "go2rtc.host", "127.0.0.1",
                    "go2rtc.port", String.valueOf(FAKE_PORT));
        }
    }

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", FAKE_PORT), 0);
        respondWith("/stream.html", "text/html", "<html>player</html>");
        respondWith("/video-rtc.js", "text/javascript", "export class VideoRTC {}");
        respondWith("/video-stream.js", "text/javascript", "import './video-rtc.js';");
        respondWith("/api/webrtc", "application/json", "{\"type\":\"answer\"}");
        respondWith("/api/frame.jpeg", "image/jpeg", "jpegbytes");
        // Admin-Pfade: der Fake WÜRDE antworten – der Proxy darf sie nie erreichen.
        respondWith("/api/streams", "application/json", "{\"garten\":{\"producers\":\"rtsp://geheim\"}}");
        respondWith("/api/config", "application/json", "{\"api\":{}}");
        respondWith("/", "text/html", "<html>go2rtc-dashboard</html>");
        server.start();
    }

    private static void respondWith(String path, String contentType, String body) {
        server.createContext(path, ex -> {
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", contentType);
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void playerSeiteUndSkripteGehenDurch() {
        given().when().get("/go2rtc/stream.html?src=garten&mode=mse")
                .then().statusCode(200).body(containsString("player"));
        given().when().get("/go2rtc/video-stream.js")
                .then().statusCode(200).body(containsString("video-rtc.js"));
        given().when().get("/go2rtc/video-rtc.js")
                .then().statusCode(200).body(containsString("VideoRTC"));
    }

    @Test
    void webrtcSignalingPerPostGehtDurch() {
        given().contentType("application/json").body("{\"type\":\"offer\"}")
                .when().post("/go2rtc/api/webrtc?src=garten")
                .then().statusCode(200);
    }

    @Test
    void standbildGehtDurch() {
        given().when().get("/go2rtc/api/frame.jpeg?src=garten").then().statusCode(200);
    }

    @Test
    void adminApiIstGesperrt() {
        // Der Kern von K1: weder lesen (RTSP-URL) noch schreiben (exec:-Stream).
        given().when().get("/go2rtc/api/streams").then().statusCode(403);
        given().when().put("/go2rtc/api/streams?name=x&src=exec:id").then().statusCode(403);
        given().when().delete("/go2rtc/api/streams?src=garten").then().statusCode(403);
        given().when().get("/go2rtc/api/config").then().statusCode(403);
    }

    @Test
    void dashboardUndUnbekanntePfadeSindGesperrt() {
        given().when().get("/go2rtc/").then().statusCode(403);
        given().when().get("/go2rtc/api/exec").then().statusCode(403);
    }

    @Test
    void erlaubterPfadNurAlsExakterMatch() {
        // Kein Präfix-Match: sonst öffnet "/stream.html" auch "/stream.htmlx" & Co.
        given().when().get("/go2rtc/stream.htmlx").then().statusCode(403);
        given().when().get("/go2rtc/stream.html/../api/streams").then().statusCode(403);
    }

    @Test
    void schreibendeMethodenAufErlaubtenPfadenSindGesperrt() {
        // stream.html ist nur lesend freigegeben.
        given().when().post("/go2rtc/stream.html").then().statusCode(403);
    }
}
