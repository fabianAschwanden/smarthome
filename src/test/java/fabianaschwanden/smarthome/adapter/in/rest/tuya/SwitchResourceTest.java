package fabianaschwanden.smarthome.adapter.in.rest.tuya;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

/**
 * Integrationstest der Schalter-Endpunkte. Im {@code %test}-Profil sind die
 * Mock-Geräte aus der Konfiguration aktiv (immer erreichbar).
 */
@QuarkusTest
class SwitchResourceTest {

    @Test
    void listet_konfigurierte_geraete() {
        given()
                .when().get("/api/switches")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(2))
                .body("id", hasItems("stehlampe", "palmenbeleuchtung"))
                .body("name", hasItems("Stehlampe", "Palmenbeleuchtung"));
    }

    @Test
    void schaltet_ein_geraet() {
        given().contentType("application/json").body("{\"state\":\"ON\"}")
                .when().post("/api/switches/stehlampe")
                .then().statusCode(200)
                .body("id", is("stehlampe"))
                .body("state", is("ON"))
                .body("online", is(true));
    }

    @Test
    void unbekannte_id_ist_404() {
        given().contentType("application/json").body("{\"state\":\"ON\"}")
                .when().post("/api/switches/gibtsnicht")
                .then().statusCode(404);
    }

    @Test
    void ungueltiger_state_ist_400() {
        given().contentType("application/json").body("{\"state\":\"BLINK\"}")
                .when().post("/api/switches/stehlampe")
                .then().statusCode(400);
    }

    @Test
    void schaltet_aus_und_status_bleibt_konsistent() {
        given().contentType("application/json").body("{\"state\":\"OFF\"}")
                .when().post("/api/switches/palmenbeleuchtung")
                .then().statusCode(200)
                .body("id", is("palmenbeleuchtung"))
                .body("state", is("OFF"));
    }

    @Test
    void homecinema_ist_kritisch() {
        given()
                .when().get("/api/switches")
                .then().statusCode(200)
                .body("find { it.id == 'homecinema' }.critical", is(true));
    }

    @Test
    void kritischer_schalter_aus_ohne_bestaetigung_ist_409() {
        given().contentType("application/json").body("{\"state\":\"OFF\"}")
                .when().post("/api/switches/homecinema")
                .then().statusCode(409);
    }

    @Test
    void kritischer_schalter_aus_mit_bestaetigung_ist_200() {
        given().contentType("application/json").body("{\"state\":\"OFF\",\"confirm\":true}")
                .when().post("/api/switches/homecinema")
                .then().statusCode(200)
                .body("state", is("OFF"));
    }

    @Test
    void kritischer_schalter_ein_ohne_bestaetigung_erlaubt() {
        given().contentType("application/json").body("{\"state\":\"ON\"}")
                .when().post("/api/switches/homecinema")
                .then().statusCode(200)
                .body("state", is("ON"));
    }
}
