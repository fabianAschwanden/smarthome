package fabianaschwanden.smarthome.domain.model.safety;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SmokeDetectorTest {

    private final Instant now = Instant.parse("2026-06-22T10:00:00Z");

    @Test
    void gueltigeInstanzBautKorrekt() {
        SmokeDetector d = new SmokeDetector("d1", "Melder Flur", "Flur", AlarmState.OK, 80, true, now);
        assertEquals("d1", d.id());
        assertEquals("Melder Flur", d.name());
        assertEquals("Flur", d.room());
        assertEquals(AlarmState.OK, d.alarm());
        assertEquals(80, d.battery());
        assertTrue(d.online());
        assertEquals(now, d.observedAt());
    }

    @Test
    void nullRoomWirdLeererString() {
        SmokeDetector d = new SmokeDetector("d1", "Melder", null, AlarmState.OK, 50, true, now);
        assertEquals("", d.room());
    }

    @Test
    void idDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new SmokeDetector(null, "Melder", "Raum", AlarmState.OK, 50, true, now));
        assertThrows(IllegalArgumentException.class,
                () -> new SmokeDetector(" ", "Melder", "Raum", AlarmState.OK, 50, true, now));
    }

    @Test
    void nameDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new SmokeDetector("d1", " ", "Raum", AlarmState.OK, 50, true, now));
    }

    @Test
    void alarmDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new SmokeDetector("d1", "Melder", "Raum", null, 50, true, now));
    }

    @Test
    void observedAtDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new SmokeDetector("d1", "Melder", "Raum", AlarmState.OK, 50, true, null));
    }

    @Test
    void onlineSetztFlag() {
        SmokeDetector d = SmokeDetector.online("d1", "Melder", "Raum", AlarmState.ALARM, 60, now);
        assertTrue(d.online());
        assertEquals(AlarmState.ALARM, d.alarm());
        assertEquals(60, d.battery());
    }

    @Test
    void offlineSetztFlag() {
        SmokeDetector d = SmokeDetector.offline("d1", "Melder", "Raum", AlarmState.OK,
                SmokeDetector.BATTERY_UNKNOWN, now);
        assertFalse(d.online());
        assertEquals(SmokeDetector.BATTERY_UNKNOWN, d.battery());
    }
}
