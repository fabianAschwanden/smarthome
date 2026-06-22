package fabianaschwanden.smarthome.adapter.in.rest.alert;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Integrationstest des Alert-Einstellungs-Endpunkts. Prüft Lesen, Speichern und dass
 * ein Test-Push ohne aktivierte Einstellungen abgelehnt wird (kein echter Push).
 */
@QuarkusTest
class AlertResourceTest {

    @Test
    void liest_und_speichert_einstellungen() {
        // Speichern (aktiviert mit Topic).
        given()
                .contentType("application/json")
                .body("{\"enabled\":true,\"ntfyTopic\":\"test-topic-xyz\"}")
                .when().put("/api/alert-settings")
                .then().statusCode(200)
                .body("enabled", is(true))
                .body("ntfyTopic", is("test-topic-xyz"));

        // Lesen liefert den gespeicherten Stand zurück.
        given()
                .when().get("/api/alert-settings")
                .then().statusCode(200)
                .body("enabled", is(true))
                .body("ntfyTopic", is("test-topic-xyz"));

        // Aufräumen: wieder deaktivieren.
        given()
                .contentType("application/json")
                .body("{\"enabled\":false,\"ntfyTopic\":\"\"}")
                .when().put("/api/alert-settings")
                .then().statusCode(200);
    }

    @Test
    void test_push_ohne_aktivierung_wird_abgelehnt() {
        // Sicherstellen, dass deaktiviert ist.
        given()
                .contentType("application/json")
                .body("{\"enabled\":false,\"ntfyTopic\":\"\"}")
                .when().put("/api/alert-settings")
                .then().statusCode(200);

        // Test-Push ist ohne aktive Einstellungen nicht möglich -> 400 (kein echter Push).
        given()
                .when().post("/api/alert-settings/test")
                .then().statusCode(400);
    }
}
