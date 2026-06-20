package fabianaschwanden.smarthome.adapter.in.rest.cover;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

/**
 * Integrationstest der Storen-Endpunkte. Im {@code %test}-Profil sind die
 * Mock-Storen aus der Konfiguration aktiv (immer erreichbar).
 */
@QuarkusTest
class CoverResourceTest {

    @Test
    void listet_konfigurierte_storen() {
        given()
                .when().get("/api/covers")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("id", org.hamcrest.Matchers.hasItem("store-links"));
    }

    @Test
    void befehl_schliessen_setzt_position_0() {
        given().contentType("application/json").body("{\"command\":\"CLOSE\"}")
                .when().post("/api/covers/store-links/command")
                .then().statusCode(200)
                .body("id", is("store-links"))
                .body("position", is(0));
    }

    @Test
    void position_setzen() {
        given().contentType("application/json").body("{\"position\":70}")
                .when().post("/api/covers/store-links/position")
                .then().statusCode(200)
                .body("position", is(70));
    }

    @Test
    void ungueltige_position_ist_400() {
        given().contentType("application/json").body("{\"position\":150}")
                .when().post("/api/covers/store-links/position")
                .then().statusCode(400);
    }

    @Test
    void unbekannte_id_ist_404() {
        given().contentType("application/json").body("{\"command\":\"OPEN\"}")
                .when().post("/api/covers/gibtsnicht/command")
                .then().statusCode(404);
    }
}
