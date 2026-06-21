package fabianaschwanden.smarthome.adapter.in.rest.camera;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Integrationstest des Kamera-Endpunkts. Liefert Metadaten samt go2rtc-Stream-Namen –
 * aber niemals RTSP-URL oder IP (die bleiben im gitignored go2rtc-Gateway).
 */
@QuarkusTest
class CameraResourceTest {

    @Test
    void listet_kameras_mit_stream_namen() {
        given()
                .when().get("/api/cameras")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("id", hasItem("garten"))
                .body("find { it.id == 'garten' }.stream", is("garten"));
    }

    @Test
    void liefert_niemals_rtsp_url_oder_ip() {
        given()
                .when().get("/api/cameras")
                .then().statusCode(200)
                // Stream-Name ist ein Alias – keine Lecks wie rtsp:// oder eine IP (Punkte).
                .body("stream", everyItem(not(containsString("rtsp"))))
                .body("stream", everyItem(not(containsString("."))));
    }
}
