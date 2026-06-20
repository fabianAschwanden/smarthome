package fabianaschwanden.smarthome.adapter.in.rest.weather;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integrationstest des Wetter-Endpunkts. Im {@code %test}-Profil liefert der
 * Mock-Gateway feste Werte für den konfigurierten Ort.
 */
@QuarkusTest
class WeatherResourceTest {

    @Test
    void liefert_vorhersage_mit_stundenverlauf() {
        given()
                .when().get("/api/weather")
                .then().statusCode(200)
                .body("location", notNullValue())
                .body("condition", notNullValue())
                .body("hours.size()", greaterThanOrEqualTo(1));
    }
}
