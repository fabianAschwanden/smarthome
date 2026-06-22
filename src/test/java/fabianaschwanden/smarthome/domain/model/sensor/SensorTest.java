package fabianaschwanden.smarthome.domain.model.sensor;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SensorTest {

    private final Instant now = Instant.parse("2026-06-22T10:00:00Z");

    @Test
    void gueltigeInstanzBautKorrekt() {
        Sensor s = new Sensor("s1", "Innensensor", "Schlafzimmer", 21.5, 45, true, now);
        assertEquals("s1", s.id());
        assertEquals("Innensensor", s.name());
        assertEquals("Schlafzimmer", s.room());
        assertEquals(21.5, s.temperature());
        assertEquals(45, s.humidity());
        assertTrue(s.online());
        assertEquals(now, s.observedAt());
    }

    @Test
    void nullRoomWirdLeererString() {
        Sensor s = new Sensor("s1", "Sensor", null, 20.0, 50, true, now);
        assertEquals("", s.room());
    }

    @Test
    void idDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class, () -> new Sensor(null, "Sensor", "Raum", 20, 50, true, now));
        assertThrows(IllegalArgumentException.class, () -> new Sensor(" ", "Sensor", "Raum", 20, 50, true, now));
    }

    @Test
    void nameDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class, () -> new Sensor("s1", " ", "Raum", 20, 50, true, now));
    }

    @Test
    void observedAtDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class, () -> new Sensor("s1", "Sensor", "Raum", 20, 50, true, null));
    }

    @Test
    void onlineSetztFlag() {
        Sensor s = Sensor.online("s1", "Sensor", "Raum", 22.0, 40, now);
        assertTrue(s.online());
        assertEquals(22.0, s.temperature());
        assertEquals(40, s.humidity());
    }

    @Test
    void offlineSetztFlagUndUnbekannteWerte() {
        Sensor s = Sensor.offline("s1", "Sensor", "Raum", Sensor.VALUE_UNKNOWN, Sensor.HUMIDITY_UNKNOWN, now);
        assertFalse(s.online());
        assertEquals(Sensor.VALUE_UNKNOWN, s.temperature());
        assertEquals(Sensor.HUMIDITY_UNKNOWN, s.humidity());
    }
}
