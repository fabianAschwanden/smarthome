package fabianaschwanden.smarthome.adapter.in.rest.battery;

import fabianaschwanden.smarthome.domain.model.battery.SunGuard;
import fabianaschwanden.smarthome.domain.port.out.battery.SunGuardRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Ohne-Sonne-Ausschalter ueber HTTP und gegen die echte Datenbank.
 *
 * <p>Geordnet und am Ende wieder ausgeschaltet: Der Zustand ist ein Singleton und
 * ueberlebt den Test - ein eingeschaltet zurueckgelassener Waechter koennte in anderen
 * Tests ueber den Scheduler-Tick am Mock-Relais schalten.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SunGuardResourceTest {

    @Inject
    SunGuardRepository repository;

    @Test
    @Order(1)
    void liefert_den_stand_des_waechters() {
        given()
                .when().get("/api/battery/sun-guard")
                .then().statusCode(200)
                .body("enabled", notNullValue())
                .body("armed", notNullValue());
    }

    @Test
    @Order(2)
    void schaltet_den_waechter_ein_und_wieder_aus() {
        given().contentType("application/json").body("{\"enabled\":true}")
                .when().put("/api/battery/sun-guard")
                .then().statusCode(200).body("enabled", is(true));

        given()
                .when().get("/api/battery/sun-guard")
                .then().statusCode(200).body("enabled", is(true));

        given().contentType("application/json").body("{\"enabled\":false}")
                .when().put("/api/battery/sun-guard")
                .then().statusCode(200).body("enabled", is(false));
    }

    @Test
    @Order(3)
    @TestTransaction
    void haelt_merker_und_ausloesezeitpunkt_fest() {
        // Gegen die echte Datenbank, nicht gegen eine Attrappe: Der erste INSERT ist die
        // Stelle, an der eine zu frueh persistierte Entity NULL-Spalten schriebe.
        Instant ausgeloest = Instant.parse("2026-09-13T16:20:00Z");

        repository.save(new SunGuard(true, true, null));
        assertTrue(repository.load().armed());

        repository.save(repository.load().trippedAt(ausgeloest));
        assertEquals(ausgeloest, repository.load().lastTrippedAt());
        assertEquals(false, repository.load().armed());
    }
}
