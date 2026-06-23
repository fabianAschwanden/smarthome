package fabianaschwanden.smarthome.adapter.in.rest.nativeview;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

/**
 * Integrationstest des Native-Endpunkts. Liefert id/name/icon + den Proxy-Pfad,
 * aber niemals die Geräte-URL/IP (die bleibt serverseitig im Reverse-Proxy).
 */
@QuarkusTest
class NativeViewResourceTest {

    @Test
    void listet_native_views_mit_proxy_pfad() {
        given()
                .when().get("/api/native")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("id", hasItem("smartfox"))
                .body("find { it.id == 'smartfox' }.name", is("SMARTFOX"))
                .body("find { it.id == 'smartfox' }.path", is("/native/smartfox/"));
    }

    @Test
    void liefert_keine_geraete_url() {
        given()
                .when().get("/api/native")
                .then().statusCode(200)
                // Pfad ist relativ (gleiche Origin) – kein http(s):// und keine IP.
                .body("path", everyItem(startsWith("/native/")))
                .body("path", everyItem(not(org.hamcrest.Matchers.containsString("http"))));
    }
}
