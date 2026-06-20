package fabianaschwanden.smarthome.adapter.in.rest.battery;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integrationstest der Batterie-Endpunkte. Im {@code %test}-Profil ist das
 * Mock-Relais aktiv. Geordnet: der Modus ist gemeinsamer Zustand des Singletons,
 * daher wird Manuell erst gesetzt, dann manuell geschaltet, zuletzt zurückgesetzt.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BatteryResourceTest {

    @Test
    @Order(1)
    void liefert_status_mit_modus_und_zustand() {
        given()
                .when().get("/api/battery")
                .then().statusCode(200)
                .body("mode", notNullValue())
                .body("desiredState", notNullValue())
                .body("changedAt", notNullValue());
    }

    @Test
    @Order(2)
    void manuelles_schalten_im_auto_modus_ist_konflikt() {
        given().contentType("application/json").body("{\"mode\":\"AUTO\"}")
                .when().put("/api/battery/mode")
                .then().statusCode(200).body("mode", is("AUTO"));

        given().contentType("application/json").body("{\"state\":\"ON\"}")
                .when().post("/api/battery/relay")
                .then().statusCode(409);
    }

    @Test
    @Order(3)
    void manuell_setzen_dann_relais_schalten() {
        given().contentType("application/json").body("{\"mode\":\"MANUAL\"}")
                .when().put("/api/battery/mode")
                .then().statusCode(200).body("mode", is("MANUAL"));

        given().contentType("application/json").body("{\"state\":\"ON\"}")
                .when().post("/api/battery/relay")
                .then().statusCode(200)
                .body("desiredState", is("ON"));

        // Zustand wieder neutralisieren, um andere Tests nicht zu beeinflussen.
        given().contentType("application/json").body("{\"mode\":\"AUTO\"}")
                .when().put("/api/battery/mode")
                .then().statusCode(200);
    }
}
