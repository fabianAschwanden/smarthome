package fabianaschwanden.smarthome.adapter.in.rest.forecast;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Die Forecast-Endpunkte über echtes HTTP. Im Testprofil liefert der
 * {@code MockIrradianceGateway} eine synthetische Glockenkurve, und ohne gelerntes Profil
 * greift der Cold Start – die Prognose ist also vorhanden und als {@code ROUGH} markiert.
 */
@QuarkusTest
class ForecastResourceTest {

    @Test
    void pvLiefertStundenUndTagessummen() {
        given()
                .when().get("/api/forecast/pv")
                .then().statusCode(200)
                .body("hours.size()", greaterThan(0))
                .body("hours[0].hour", notNullValue())
                .body("hours[0].expectedPvWatt", notNullValue())
                .body("hours[0].gti", notNullValue())
                .body("todayKwh", notNullValue())
                .body("tomorrowKwh", notNullValue())
                .body("computedAt", notNullValue())
                // Ohne gelerntes Profil ist die Prognose bewusst als grob gekennzeichnet.
                .body("confidence", equalTo("ROUGH"));
    }

    @Test
    void surplusLiefertBaselineUndFenster() {
        given()
                .when().get("/api/forecast/surplus")
                .then().statusCode(200)
                // 24 Slots je Gruppe – die Vergleichskurve fuers UI.
                .body("baselineWeekdayWatt", hasSize(24))
                .body("baselineWeekendWatt", hasSize(24))
                .body("windows", notNullValue());
    }

    @Test
    void applyLegtEinenBatterieZeitplanAn() {
        // Der Mock liefert einen sonnigen Tag, die Baseline ist ohne Historie 0 -> es
        // gibt ein Fenster, also eine Empfehlung.
        String id = given()
                .when().post("/api/forecast/recommendation/apply")
                .then().statusCode(200)
                .body("type", equalTo("COUNTDOWN"))
                .body("action", equalTo("ON"))
                .body("enabled", equalTo(true))
                .body("fireAt", notNullValue())
                .body("time", nullValue())
                .extract().path("id");

        // Der Zeitplan muss anschliessend in Use Case 14 auftauchen - dort gehoert die
        // Ausfuehrung hin, dieser Use Case schaltet nicht selbst.
        given()
                .when().get("/api/battery-schedules")
                .then().statusCode(200)
                .body("findAll { it.id == '" + id + "' }.size()", equalTo(1));
    }

    @Test
    void kurveIstPlausibel() {
        // Abnahmekriterium aus PLAN.md Etappe 5: "eine plausible Kurve". Der Mock liefert
        // einen wolkenlosen Tag - nachts muss die Leistung 0 sein, tagsueber im kW-Bereich.
        java.util.List<Float> watt = given()
                .when().get("/api/forecast/pv")
                .then().statusCode(200)
                .extract().jsonPath().getList("hours.expectedPvWatt", Float.class);
        java.util.List<String> stunden = given()
                .when().get("/api/forecast/pv")
                .then().extract().jsonPath().getList("hours.hour", String.class);

        double maximum = watt.stream().mapToDouble(Float::doubleValue).max().orElse(0);
        org.junit.jupiter.api.Assertions.assertTrue(maximum > 1000,
                "Mittags muss die Anlage im kW-Bereich liegen, war: " + maximum);

        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        for (int i = 0; i < stunden.size(); i++) {
            int stundeLokal = java.time.Instant.parse(stunden.get(i)).atZone(zone).getHour();
            if (stundeLokal == 2) {
                org.junit.jupiter.api.Assertions.assertEquals(0.0, watt.get(i), 0.001,
                        "um 02:00 lokal darf keine Leistung erwartet werden");
            }
        }
    }

    @Test
    void openApiBeschreibtDieEndpunkte() {
        // Abnahmekriterium aus PLAN.md Etappe 5: Beschreibungen an den Resources.
        given()
                .when().get("/q/openapi")
                .then().statusCode(200)
                .body(containsString("/api/forecast/pv"))
                .body(containsString("/api/forecast/surplus"))
                .body(containsString("/api/forecast/recommendation/apply"))
                .body(containsString("Ladeempfehlung als Batterie-Zeitplan"));
    }

    @Test
    void stundenTragenDenStundenbeginnAlsZeitstempel() {
        // Das Frontend zeichnet daraus die Kurve - die Zeitpunkte muessen aufsteigend und
        // stundenweise sein, sonst springt der Graph.
        java.util.List<String> stunden = given()
                .when().get("/api/forecast/pv")
                .then().statusCode(200)
                .extract().jsonPath().getList("hours.hour", String.class);

        java.time.Instant vorher = null;
        for (String wert : stunden) {
            java.time.Instant aktuell = java.time.Instant.parse(wert);
            if (vorher != null) {
                org.junit.jupiter.api.Assertions.assertEquals(
                        3600, aktuell.getEpochSecond() - vorher.getEpochSecond(),
                        "Stundenabstand bei " + wert);
            }
            vorher = aktuell;
        }
    }
}
