package fabianaschwanden.smarthome.adapter.in.rest.sensor;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/** Integrationstest des Sensor-Endpunkts. Im {@code %test}-Profil ist der Mock-Sensor aktiv. */
@QuarkusTest
class SensorResourceTest {

    @Test
    void listet_sensoren_mit_messwerten() {
        given()
                .when().get("/api/sensors")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("id", hasItem("innen"))
                .body("find { it.id == 'innen' }.online", is(true))
                .body("find { it.id == 'innen' }.humidity", is(45));
    }
}
