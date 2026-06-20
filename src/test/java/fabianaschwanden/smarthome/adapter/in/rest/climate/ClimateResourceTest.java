package fabianaschwanden.smarthome.adapter.in.rest.climate;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/** Integrationstest der Klima-Endpunkte. Im {@code %test}-Profil ist die Mock-Klima aktiv. */
@QuarkusTest
class ClimateResourceTest {

    @Test
    void listet_konfigurierte_klimaanlagen() {
        given()
                .when().get("/api/climate")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("id", hasItem("klima"));
    }

    @Test
    void einschalten() {
        given().contentType("application/json").body("{\"on\":true}")
                .when().post("/api/climate/klima/power")
                .then().statusCode(200)
                .body("power", is(true));
    }

    @Test
    void modus_setzen() {
        given().contentType("application/json").body("{\"mode\":\"COOL\"}")
                .when().post("/api/climate/klima/mode")
                .then().statusCode(200)
                .body("mode", is("COOL"));
    }

    @Test
    void soll_temperatur_setzen() {
        given().contentType("application/json").body("{\"temperature\":24}")
                .when().post("/api/climate/klima/target")
                .then().statusCode(200)
                .body("targetTemp", is(24));
    }

    @Test
    void ungueltige_temperatur_ist_400() {
        given().contentType("application/json").body("{\"temperature\":40}")
                .when().post("/api/climate/klima/target")
                .then().statusCode(400);
    }

    @Test
    void unbekannte_id_ist_404() {
        given().contentType("application/json").body("{\"on\":true}")
                .when().post("/api/climate/gibtsnicht/power")
                .then().statusCode(404);
    }
}
