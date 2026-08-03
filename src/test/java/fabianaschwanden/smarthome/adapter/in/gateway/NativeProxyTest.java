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
import static org.hamcrest.Matchers.not;

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
                    "nativeview.targets[0].path", "/index.shtml",
                    "smarthome.nativeview-style-src-extra", "https://css.example");
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
        // Absoluter AJAX-Pfad der Fremd-UI (XHR auf /values.xml) – wird via Referer geproxyt.
        server.createContext("/values.xml", ex -> {
            byte[] out = "<root><power>1234</power></root>".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/xml");
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        // Spiegelt die empfangenen Request-Header zurück – so lässt sich prüfen, was der
        // Proxy ans Gerät durchlässt (Cookie/Authorization dürfen NIE ankommen).
        server.createContext("/echo-headers.txt", ex -> {
            StringBuilder sb = new StringBuilder();
            ex.getRequestHeaders().forEach((k, v) -> sb.append(k.toLowerCase()).append('\n'));
            byte[] out = sb.toString().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/plain");
            // Gerät versucht, ein Cookie in die App-Origin zu setzen -> muss gestrippt werden.
            ex.getResponseHeaders().add("Set-Cookie", "geraet=boese; Path=/");
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        // Schaltbefehl des Geräts – über den Referer-Fallback NIE erreichbar.
        server.createContext("/setswrel.cgi", ex -> {
            byte[] out = "geschaltet".getBytes(StandardCharsets.UTF_8);
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
        // Invariante: auf Proxy-Antworten darf KEIN Frame-Blocker liegen - weder das DENY
        // des Geraets noch das SAMEORIGIN, das die App global setzt. Die Fremd-UI wird als
        // iframe eingebettet; jeder Frame-Blocker laesst den Browser mit "Dieser Inhalt ist
        // blockiert" abbrechen. Genau das war passiert, nachdem der globale Header
        // eingefuehrt und dieser Test darauf angepasst wurde - der Test hatte die
        // Invariante vorher korrekt geschuetzt.
        given()
                .when().get("/native/fake/index.shtml")
                .then().statusCode(200)
                .header("X-Frame-Options", is((String) null));
    }

    @Test
    void cspDerProxyAntwortVerbietetDasFramenNicht() {
        // frame-ancestors ist der moderne Frame-Blocker - in dieser CSP hat er nichts
        // verloren, sonst blockiert der Browser die Einbettung.
        given().when().get("/native/fake/index.shtml")
                .then().statusCode(200)
                .header("Content-Security-Policy", not(containsString("frame-ancestors")));
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

    @Test
    void absoluterPfadMitNativeRefererWirdGeproxyt() {
        // XHR der Fremd-UI auf /values.xml (kein /native/-Präfix) -> via Referer ans Gerät.
        given()
                .header("Referer", "http://localhost/native/fake/index.shtml")
                .when().get("/values.xml")
                .then().statusCode(200)
                .body(containsString("<power>1234</power>"));
    }

    @Test
    void sessionHeaderErreichenDasGeraetNie() {
        // H3: ein loggendes/kompromittiertes Gerät darf keine App-Session ernten.
        String empfangen = given()
                .header("Cookie", "q_session=geheim")
                .header("Authorization", "Bearer geheim")
                .header("X-Forwarded-For", "10.0.0.1")
                .header("Accept-Language", "de-CH")
                .when().get("/native/fake/echo-headers.txt")
                .then().statusCode(200)
                .extract().asString();
        org.junit.jupiter.api.Assertions.assertFalse(empfangen.contains("cookie"), empfangen);
        org.junit.jupiter.api.Assertions.assertFalse(empfangen.contains("authorization"), empfangen);
        org.junit.jupiter.api.Assertions.assertFalse(empfangen.contains("x-forwarded-for"), empfangen);
        // Harmlose Header gehen weiterhin durch, sonst bricht die Fremd-UI.
        org.junit.jupiter.api.Assertions.assertTrue(empfangen.contains("accept-language"), empfangen);
    }

    @Test
    void setCookieDesGeraetsWirdGestrippt() {
        // H3: sonst könnte das Gerät Cookies in die App-Domain injizieren.
        given().when().get("/native/fake/echo-headers.txt")
                .then().statusCode(200)
                .header("Set-Cookie", is((String) null));
    }

    @Test
    void fallbackSchaltetKeineGeraete() {
        // H4: der Referer ist frei setzbar – ohne Einschränkung wäre das ein offener Proxy.
        given().header("Referer", "http://localhost/native/fake/index.shtml")
                .when().get("/setswrel.cgi?rel=1&state=1")
                .then().statusCode(404); // nicht ans Gerät, sondern Frontend-404
    }

    @Test
    void fallbackNurLesend() {
        // H4: schreibende Methoden gehören über den direkten /native/-Pfad, nicht hierher.
        given().header("Referer", "http://localhost/native/fake/index.shtml")
                .when().post("/values.xml")
                .then().statusCode(404);
    }

    @Test
    void fallbackPruftDenRefererPfadNichtIrgendeinVorkommen() {
        // H4: 'indexOf' hätte auch bei /native/fake/ im Query oder Fragment gegriffen.
        given().header("Referer", "http://boese.example/?next=/native/fake/index.shtml")
                .when().get("/values.xml")
                .then().statusCode(404);
    }

    @Test
    void fallbackLehntTraversalAb() {
        given().header("Referer", "http://localhost/native/fake/index.shtml")
                .when().get("/assets/../values.xml")
                .then().statusCode(404);
    }

    @Test
    void crossSiteNavigationWirdAbgewiesen() {
        // H5: SameSite=Lax schickt das Cookie bei Top-Level-GET mit -> präparierter Link
        // könnte sonst per /native/fake/setswrel.cgi?... schalten.
        given().header("Sec-Fetch-Site", "cross-site")
                .when().get("/native/fake/index.shtml")
                .then().statusCode(403);
        // Aus dem eigenen iframe (same-origin) bleibt alles offen.
        given().header("Sec-Fetch-Site", "same-origin")
                .when().get("/native/fake/index.shtml")
                .then().statusCode(200);
    }

    @Test
    void proxyAntwortTraegtEigeneCsp() {
        // H7: begrenzt die ZIELE der Fremd-UI auf die eigene Origin (keine Exfiltration).
        given().when().get("/native/fake/index.shtml")
                .then().statusCode(200)
                .header("Content-Security-Policy", containsString("connect-src 'self'"))
                .header("Content-Security-Policy", containsString("object-src 'none'"))
                .header("X-Content-Type-Options", is("nosniff"));
    }

    @Test
    void cspErlaubtKonfiguriertesFremdStylesheet() {
        // Geraete-UIs laden ihr CSS teils beim Hersteller (SMARTFOX: my.smartfox.at).
        // Ohne diese Ausnahme rendert die View unformatiert - genau das war nach der
        // Einfuehrung der CSP passiert. Exfiltrationspfade (connect-src/img via
        // default-src) bleiben trotzdem zu.
        given().when().get("/native/fake/index.shtml")
                .then().statusCode(200)
                .header("Content-Security-Policy", containsString("style-src 'self' 'unsafe-inline' data: https://css.example"))
                .header("Content-Security-Policy", containsString("connect-src 'self'"));
    }

    @Test
    void appPfadeWerdenNichtGekapert() {
        // Trotz Native-Referer darf /api/... NIE ans Gerät gehen (echte App-Route).
        given()
                .header("Referer", "http://localhost/native/fake/index.shtml")
                .when().get("/api/cameras")
                .then().statusCode(200)
                .body(containsString("garten")); // echte Kamera-API, nicht das Gerät
    }
}
