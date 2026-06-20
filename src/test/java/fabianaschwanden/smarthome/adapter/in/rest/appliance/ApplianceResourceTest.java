package fabianaschwanden.smarthome.adapter.in.rest.appliance;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

/**
 * Integrationstest der Anlagen-Endpunkte. Im {@code %test}-Profil sind die
 * Mock-Anlagen (Whirlpool, Schwimmbecken) aus der Konfiguration aktiv.
 */
@QuarkusTest
class ApplianceResourceTest {

    @Test
    void listet_whirlpool_und_pool() {
        given()
                .when().get("/api/appliances")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(2))
                .body("id", hasItems("whirlpool", "pool"));
    }

    @Test
    void schaltet_eine_funktion() {
        given().contentType("application/json").body("{\"state\":\"ON\"}")
                .when().post("/api/appliances/whirlpool/functions/HEATER")
                .then().statusCode(200)
                .body("id", is("whirlpool"))
                .body("functions.HEATER", is("ON"));
    }

    @Test
    void nicht_vorhandene_funktion_ist_400() {
        // Schwimmbecken hat kein MASSAGE.
        given().contentType("application/json").body("{\"state\":\"ON\"}")
                .when().post("/api/appliances/pool/functions/MASSAGE")
                .then().statusCode(400);
    }

    @Test
    void unbekannte_anlage_ist_404() {
        given().contentType("application/json").body("{\"state\":\"ON\"}")
                .when().post("/api/appliances/gibtsnicht/functions/PUMP")
                .then().statusCode(404);
    }

    @Test
    void beheizte_anlage_liefert_temperatur() {
        given()
                .when().get("/api/appliances")
                .then().statusCode(200)
                .body("find { it.id == 'whirlpool' }.temperature.min", is(30))
                .body("find { it.id == 'whirlpool' }.temperature.max", is(40));
    }

    @Test
    void soll_temperatur_setzen() {
        given().contentType("application/json").body("{\"target\":36}")
                .when().post("/api/appliances/whirlpool/temperature")
                .then().statusCode(200)
                .body("temperature.target", is(36));
    }

    @Test
    void temperatur_ausserhalb_bereich_ist_400() {
        // Whirlpool-Bereich 30..40 °C.
        given().contentType("application/json").body("{\"target\":50}")
                .when().post("/api/appliances/whirlpool/temperature")
                .then().statusCode(400);
    }
}
