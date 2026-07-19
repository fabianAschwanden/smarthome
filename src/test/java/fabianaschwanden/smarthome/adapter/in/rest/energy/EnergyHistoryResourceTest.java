package fabianaschwanden.smarthome.adapter.in.rest.energy;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

/** Integrationstest des Energie-Verlauf-Endpunkts (Zeitreihe leer/mock im %test-Profil). */
@QuarkusTest
class EnergyHistoryResourceTest {

    @Test
    void tagesverlaufOhneParameterDefaultDay() {
        given()
                .when().get("/api/energy/history")
                .then().statusCode(200)
                .body("range", is("day"))
                .body("buckets.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void wochenverlaufHatSiebenBuckets() {
        given()
                .when().get("/api/energy/history?range=week")
                .then().statusCode(200)
                .body("range", is("week"))
                .body("buckets.size()", is(7));
    }

    @Test
    void monatsverlaufHatDreissigBuckets() {
        given()
                .when().get("/api/energy/history?range=month")
                .then().statusCode(200)
                .body("range", is("month"))
                .body("buckets.size()", is(30));
    }

    @Test
    void bucketHatKwhFelder() {
        given()
                .when().get("/api/energy/history?range=week")
                .then().statusCode(200)
                .body("buckets[0].start", org.hamcrest.Matchers.notNullValue())
                .body("buckets[0].pvKwh", org.hamcrest.Matchers.notNullValue())
                .body("buckets[0].consumptionKwh", org.hamcrest.Matchers.notNullValue())
                .body("buckets[0].selfUseKwh", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void tagesansichtLiefertSamplesFuerDieLeistungskurve() {
        given()
                .when().get("/api/energy/history?range=day")
                .then().statusCode(200)
                .body("samples", org.hamcrest.Matchers.notNullValue());
        // Woche: keine Roh-Messpunkte (nur Tages-Buckets).
        given()
                .when().get("/api/energy/history?range=week")
                .then().statusCode(200)
                .body("samples.size()", is(0));
    }

    @Test
    void unbekannterBereichIst400() {
        given()
                .when().get("/api/energy/history?range=jahr")
                .then().statusCode(400);
    }
}
