package fabianaschwanden.smarthome.adapter.in.rest.info;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

/** Integrationstest des Release-Info-Endpunkts. Ohne gesetztes APP_VERSION -> Default „dev". */
@QuarkusTest
class InfoResourceTest {

    @Test
    void liefertVersionUndBuildZeit() {
        given()
                .when().get("/api/info")
                .then().statusCode(200)
                .body("version", notNullValue())
                .body("builtAt", notNullValue());
    }
}
