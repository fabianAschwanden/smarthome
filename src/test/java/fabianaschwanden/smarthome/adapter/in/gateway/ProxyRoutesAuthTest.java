package fabianaschwanden.smarthome.adapter.in.gateway;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

/**
 * Nagelt fest, dass die HTTP-Auth-Policy auch die Vert.x-Routen der beiden Proxys erfasst.
 *
 * <p>Das war offen (Review M2): {@code Go2rtcProxy} und {@code NativeProxy} registrieren
 * ihre Routen über {@code @Observes Router} statt als JAX-RS-Ressource. Griffe die
 * {@code /*}-Permission dort nicht, wären {@code /go2rtc/*} und {@code /native/*} in
 * Produktion unauthentifiziert erreichbar – zusammen mit der go2rtc-Admin-API (K1) wäre
 * das kritisch. Erwartet wird 401 (oder 302 auf den IdP beim BFF-Redirect).
 *
 * <p>Die Policy wird hier direkt gesetzt statt über {@code %prod}, damit der Test ohne
 * echten IdP läuft: geprüft wird das Routing/Handler-Ordering, nicht der OIDC-Flow.
 */
@QuarkusTest
@TestProfile(ProxyRoutesAuthTest.Profile.class)
class ProxyRoutesAuthTest {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.http.auth.permission.authenticated.paths", "/*",
                    "quarkus.http.auth.permission.authenticated.policy", "authenticated",
                    "quarkus.http.auth.permission.health.paths", "/q/health,/q/health/*",
                    "quarkus.http.auth.permission.health.policy", "permit",
                    "nativeview.targets[0].id", "fake",
                    "nativeview.targets[0].name", "Fake",
                    "nativeview.targets[0].url", "http://127.0.0.1:18097",
                    "nativeview.targets[0].path", "/index.shtml");
        }
    }

    private static org.hamcrest.Matcher<Integer> abgewiesen() {
        return anyOf(is(401), is(302));
    }

    @Test
    void go2rtcProxyVerlangtAuth() {
        given().when().get("/go2rtc/stream.html?src=garten")
                .then().statusCode(abgewiesen());
    }

    @Test
    void go2rtcAdminApiVerlangtAuth() {
        given().when().get("/go2rtc/api/streams")
                .then().statusCode(abgewiesen());
    }

    @Test
    void nativeProxyVerlangtAuth() {
        given().when().get("/native/fake/index.shtml")
                .then().statusCode(abgewiesen());
    }

    @Test
    void refererFallbackVerlangtAuth() {
        // Der Fallback hängt an einer catch-all-Route – auch die darf nicht vorbeigehen.
        given().header("Referer", "http://localhost/native/fake/index.shtml")
                .when().get("/values.xml")
                .then().statusCode(abgewiesen());
    }

    @Test
    void apiVerlangtAuth() {
        given().when().get("/api/cameras").then().statusCode(abgewiesen());
    }

    @Test
    void healthBleibtOffen() {
        // Gegenprobe: der Orchestrator muss ohne Login prüfen können.
        given().when().get("/q/health/ready").then().statusCode(200);
    }
}
