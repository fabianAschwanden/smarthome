package fabianaschwanden.smarthome.adapter.in.rest.safety;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/** Integrationstest des Sicherheits-Endpunkts. Im {@code %test}-Profil ist der Mock-Melder aktiv. */
@QuarkusTest
class SafetyResourceTest {

    @Test
    void listet_rauchmelder_mit_status() {
        given()
                .when().get("/api/safety/smoke")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("id", hasItem("rauchmelder"))
                .body("find { it.id == 'rauchmelder' }.alarm", is("OK"))
                .body("find { it.id == 'rauchmelder' }.online", is(true));
    }
}
