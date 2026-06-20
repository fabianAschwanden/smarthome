package fabianaschwanden.smarthome.adapter.in.rest.energy;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integrationstest des Energie-Endpunkts. Im {@code %test}-Profil sind die
 * Mock-Quellen aktiv ({@code @UnlessBuildProfile("prod")}), daher liefert der
 * Endpunkt beide Quellen samt Vergleich.
 */
@QuarkusTest
class EnergyResourceTest {

    @Test
    void liefert_beide_quellen_mit_vergleich() {
        given()
                .when().get("/api/energy/current")
                .then().statusCode(200)
                .body("timestamp", notNullValue())
                .body("readings.size()", is(2))
                .body("readings.source", hasItems("FRONIUS", "SMARTFOX"))
                .body("readings.status", hasItems("OK"))
                .body("comparison", notNullValue());
    }
}
