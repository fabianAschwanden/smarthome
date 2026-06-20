package fabianaschwanden.smarthome.adapter.in.rest.itemimage;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Integrationstest der Item-Bild-Endpunkte (typ-übergreifend, id = Geräte-ID).
 * Voller Lebenszyklus: anlegen, lesen, ersetzen, löschen + Validierung.
 */
@QuarkusTest
class ItemImageResourceTest {

    private static final String PNG =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    @Test
    void unbekanntes_item_ist_404() {
        given()
                .when().get("/api/items/gibtsnicht/image")
                .then().statusCode(404);
    }

    @Test
    void anlegen_lesen_loeschen() {
        given().contentType("application/json").body("{\"dataUrl\":\"" + PNG + "\"}")
                .when().put("/api/items/test-lampe/image")
                .then().statusCode(200)
                .body("itemId", is("test-lampe"))
                .body("dataUrl", is(PNG));

        given()
                .when().get("/api/items/test-lampe/image")
                .then().statusCode(200)
                .body("dataUrl", is(PNG));

        given()
                .when().delete("/api/items/test-lampe/image")
                .then().statusCode(204);

        given()
                .when().get("/api/items/test-lampe/image")
                .then().statusCode(404);
    }

    @Test
    void kein_bild_data_url_ist_400() {
        given().contentType("application/json").body("{\"dataUrl\":\"not-an-image\"}")
                .when().put("/api/items/test-lampe/image")
                .then().statusCode(400);
    }
}
