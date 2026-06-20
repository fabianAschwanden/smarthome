package fabianaschwanden.smarthome.adapter.in.rest.schedule;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/** Integrationstest des Schedule-CRUD gegen die echte Persistenz (Dev Services). */
@QuarkusTest
class ScheduleResourceTest {

    @Test
    void anlegen_auflisten_aktivieren_loeschen() {
        String id = given().contentType("application/json")
                .body("{\"switchId\":\"stehlampe\",\"type\":\"SCHEDULE\",\"action\":\"ON\","
                        + "\"time\":\"07:30\",\"weekdays\":[\"MONDAY\",\"FRIDAY\"]}")
                .when().post("/api/schedules")
                .then().statusCode(200)
                .body("type", is("SCHEDULE"))
                .body("time", is("07:30"))
                .body("enabled", is(true))
                .body("id", notNullValue())
                .extract().path("id");

        given().queryParam("switchId", "stehlampe")
                .when().get("/api/schedules")
                .then().statusCode(200)
                .body("id", org.hamcrest.Matchers.hasItem(id));

        given().when().put("/api/schedules/" + id + "/enabled/false")
                .then().statusCode(200).body("enabled", is(false));

        given().when().delete("/api/schedules/" + id)
                .then().statusCode(204);
    }

    @Test
    void countdown_anlegen() {
        given().contentType("application/json")
                .body("{\"switchId\":\"stehlampe\",\"type\":\"COUNTDOWN\",\"action\":\"OFF\","
                        + "\"countdownSeconds\":600}")
                .when().post("/api/schedules")
                .then().statusCode(200)
                .body("type", is("COUNTDOWN"))
                .body("fireAt", notNullValue());
    }

    @Test
    void schedule_ohne_uhrzeit_ist_400() {
        given().contentType("application/json")
                .body("{\"switchId\":\"stehlampe\",\"type\":\"SCHEDULE\",\"action\":\"ON\"}")
                .when().post("/api/schedules")
                .then().statusCode(400);
    }

    @Test
    void unbekannte_id_loeschen_ist_404() {
        given().when().delete("/api/schedules/" + java.util.UUID.randomUUID())
                .then().statusCode(404);
    }
}
