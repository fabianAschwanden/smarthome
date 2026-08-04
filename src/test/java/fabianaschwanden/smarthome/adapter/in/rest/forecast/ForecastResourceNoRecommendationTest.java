package fabianaschwanden.smarthome.adapter.in.rest.forecast;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;

/**
 * Der 409-Fall: „Übernehmen“ ohne vorliegende Empfehlung.
 *
 * <p>Die Schwelle wird so hoch gesetzt, dass kein Fenster entsteht – so läuft der Fall
 * über echtes HTTP statt über einen ausgetauschten Port, und die Kette vom Fachfehler
 * bis zum Statuscode wird wirklich geprüft.
 */
@QuarkusTest
@TestProfile(ForecastResourceNoRecommendationTest.Profile.class)
class ForecastResourceNoRecommendationTest {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("forecast.surplus.min-watt", "999999");
        }
    }

    @Test
    void applyOhneEmpfehlungGibt409() {
        given()
                .when().post("/api/forecast/recommendation/apply")
                .then().statusCode(409)
                .body(containsString("keine Ladeempfehlung"));
    }

    @Test
    void surplusLiefertDannKeineEmpfehlungAberBleibtGueltig() {
        given()
                .when().get("/api/forecast/surplus")
                .then().statusCode(200)
                .body("recommendation", nullValue())
                .body("windows.size()", org.hamcrest.Matchers.equalTo(0));
    }
}
