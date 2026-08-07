package fabianaschwanden.smarthome.adapter.in.rest.applianceschedule;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integrationstest der Wellness-Zeitsteuerung: Die Auftraege entstehen aus dem
 * Ueberschussfenster der Prognose, sind danach abrufbar und lassen sich verwerfen.
 */
@QuarkusTest
class ApplianceScheduleResourceTest {

    @Test
    void auftraege_entstehen_aus_dem_ueberschussfenster_und_lassen_sich_verwerfen() {
        // Im Testprofil rechnet die Prognose mit Mock-Daten; liegt kein Fenster vor,
        // antwortet der Endpunkt mit 409 - beides ist ein gueltiger Ausgang.
        int status = given().contentType("application/json")
                .when().post("/api/forecast/wellness-surplus/apply")
                .then().extract().statusCode();

        if (status == 409) {
            given().when().get("/api/appliance-schedules").then().statusCode(200);
            return;
        }

        // Alles ausser 200 und 409 ist ein Fehler - ein 500 hier hat schon einmal einen
        // echten Defekt im Persistence-Adapter aufgedeckt.
        org.junit.jupiter.api.Assertions.assertEquals(200, status);
        String id = given().when().get("/api/appliance-schedules")
                .then().statusCode(200)
                .body("[0].targetTemp", notNullValue())
                .body("[0].fireAt", notNullValue())
                .extract().path("[0].id");

        given().when().delete("/api/appliance-schedules/" + id).then().statusCode(204);
    }

    @Test
    void liefert_die_liste_auch_wenn_nichts_ansteht() {
        given().when().get("/api/appliance-schedules").then().statusCode(200);
    }
}
