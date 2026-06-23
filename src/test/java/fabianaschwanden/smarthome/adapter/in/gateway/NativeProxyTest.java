package fabianaschwanden.smarthome.adapter.in.gateway;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Integrationstest des Native-Reverse-Proxys gegen einen lokalen Fake-HTTP-Server
 * (festes Ziel auf 127.0.0.1:18099, kein echtes Gerät). Prüft: HTML wird mit
 * {@code <base>} ausgeliefert, Frame-Blocker-Header werden entfernt, Assets gehen
 * durch, unbekannte id liefert 404.
 */
@QuarkusTest
@TestProfile(NativeProxyTest.Profile.class)
class NativeProxyTest {

    static final int FAKE_PORT = 18099;
    private static HttpServer server;

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "nativeview.targets[0].id", "fake",
                    "nativeview.targets[0].name", "Fake",
                    "nativeview.targets[0].url", "http://127.0.0.1:" + FAKE_PORT,
                    "nativeview.targets[0].path", "/index.shtml");
        }
    }

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", FAKE_PORT), 0);
        server.createContext("/index.shtml", ex -> {
            byte[] out = "<html><head><title>SMARTFOX</title></head><body>Werte</body></html>"
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html");
            ex.getResponseHeaders().add("X-Frame-Options", "DENY"); // muss vom Proxy entfernt werden
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        server.createContext("/style.css", ex -> {
            byte[] out = "body{color:red}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/css");
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void htmlWirdMitBaseTagAusgeliefert() {
        given()
                .when().get("/native/fake/index.shtml")
                .then().statusCode(200)
                .body(containsString("<base href=\"/native/fake/\">"))
                .body(containsString("SMARTFOX"));
    }

    @Test
    void frameBlockerHeaderWirdEntfernt() {
        given()
                .when().get("/native/fake/index.shtml")
                .then().statusCode(200)
                .header("X-Frame-Options", is((String) null));
    }

    @Test
    void assetWirdDurchgereicht() {
        given()
                .when().get("/native/fake/style.css")
                .then().statusCode(200)
                .body(containsString("color:red"));
    }

    @Test
    void unbekannteIdLiefert404() {
        given()
                .when().get("/native/gibtsnicht/index.shtml")
                .then().statusCode(404);
    }
}
