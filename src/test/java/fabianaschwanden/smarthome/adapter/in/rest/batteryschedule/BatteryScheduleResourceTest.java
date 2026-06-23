package fabianaschwanden.smarthome.adapter.in.rest.batteryschedule;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Integrationstest des Batterie-Zeitsteuerungs-Endpunkts: anlegen, listen, aktivieren,
 * löschen sowie Validierung (fehlende Felder → 400, unbekannte ID → 404).
 */
@QuarkusTest
class BatteryScheduleResourceTest {

    @Test
    void anlegenListenAktivierenLoeschen() {
        // SCHEDULE anlegen (Laden AUS um 22:00 täglich)
        String id = given()
                .contentType("application/json")
                .body("{\"type\":\"SCHEDULE\",\"action\":\"OFF\",\"time\":\"22:00\"}")
                .when().post("/api/battery-schedules")
                .then().statusCode(200)
                .body("type", is("SCHEDULE"))
                .body("action", is("OFF"))
                .body("time", is("22:00"))
                .body("enabled", is(true))
                .extract().path("id");

        // listen
        given().when().get("/api/battery-schedules")
                .then().statusCode(200)
                .body("findAll { it.id == '" + id + "' }.size()", is(1));

        // deaktivieren
        given().when().put("/api/battery-schedules/" + id + "/enabled/false")
                .then().statusCode(200).body("enabled", is(false));

        // löschen
        given().when().delete("/api/battery-schedules/" + id).then().statusCode(204);
    }

    @Test
    void countdownBrauchtSekunden_sonst400() {
        given()
                .contentType("application/json")
                .body("{\"type\":\"COUNTDOWN\",\"action\":\"ON\"}") // countdownSeconds fehlt
                .when().post("/api/battery-schedules")
                .then().statusCode(400);
    }

    @Test
    void unbekannteIdLiefert404() {
        given()
                .when().put("/api/battery-schedules/00000000-0000-0000-0000-000000000000/enabled/false")
                .then().statusCode(404);
    }
}
