package fabianaschwanden.smarthome.domain.model.climate;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class ClimateTest {

    private final Instant now = Instant.parse("2026-06-22T10:00:00Z");

    @Test
    void gueltigeInstanzBautKorrekt() {
        Climate c = new Climate("ac1", "Wohnzimmer-Klima", "Wohnzimmer", true,
                ClimateMode.COOL, 22, 24, true, now);
        assertEquals("ac1", c.id());
        assertEquals("Wohnzimmer-Klima", c.name());
        assertEquals("Wohnzimmer", c.room());
        assertEquals(true, c.power());
        assertEquals(ClimateMode.COOL, c.mode());
        assertEquals(22, c.targetTemp());
        assertEquals(24, c.currentTemp());
        assertEquals(true, c.online());
        assertEquals(now, c.observedAt());
    }

    @Test
    void nullRoomWirdLeererString() {
        Climate c = new Climate("ac1", "Klima", null, false, ClimateMode.AUTO, 20,
                Climate.TEMP_UNKNOWN, false, now);
        assertEquals("", c.room());
    }

    @Test
    void idDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class, () -> new Climate(
                " ", "Klima", "Raum", true, ClimateMode.COOL, 22, 22, true, now));
        assertThrows(IllegalArgumentException.class, () -> new Climate(
                null, "Klima", "Raum", true, ClimateMode.COOL, 22, 22, true, now));
    }

    @Test
    void nameDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class, () -> new Climate(
                "ac1", " ", "Raum", true, ClimateMode.COOL, 22, 22, true, now));
    }

    @Test
    void modeDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class, () -> new Climate(
                "ac1", "Klima", "Raum", true, null, 22, 22, true, now));
    }

    @Test
    void targetTempUnterMinWirft() {
        assertThrows(IllegalArgumentException.class, () -> new Climate(
                "ac1", "Klima", "Raum", true, ClimateMode.COOL, Climate.MIN_TEMP - 1, 22, true, now));
    }

    @Test
    void targetTempUeberMaxWirft() {
        assertThrows(IllegalArgumentException.class, () -> new Climate(
                "ac1", "Klima", "Raum", true, ClimateMode.COOL, Climate.MAX_TEMP + 1, 22, true, now));
    }

    @Test
    void observedAtDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class, () -> new Climate(
                "ac1", "Klima", "Raum", true, ClimateMode.COOL, 22, 22, true, null));
    }

    @Test
    void requireValidTargetAkzeptiertGrenzen() {
        assertEquals(Climate.MIN_TEMP, Climate.requireValidTarget(Climate.MIN_TEMP));
        assertEquals(Climate.MAX_TEMP, Climate.requireValidTarget(Climate.MAX_TEMP));
    }

    @Test
    void requireValidTargetWirftAusserhalb() {
        assertThrows(IllegalArgumentException.class, () -> Climate.requireValidTarget(Climate.MIN_TEMP - 1));
        assertThrows(IllegalArgumentException.class, () -> Climate.requireValidTarget(Climate.MAX_TEMP + 1));
    }
}
