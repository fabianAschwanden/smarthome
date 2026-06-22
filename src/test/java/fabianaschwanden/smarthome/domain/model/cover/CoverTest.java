package fabianaschwanden.smarthome.domain.model.cover;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class CoverTest {

    private final Instant now = Instant.parse("2026-06-22T10:00:00Z");

    @Test
    void gueltigeInstanzBautKorrekt() {
        Cover c = new Cover("c1", "Store Süd", "Wohnzimmer", 50, true, now);
        assertEquals("c1", c.id());
        assertEquals("Store Süd", c.name());
        assertEquals("Wohnzimmer", c.room());
        assertEquals(50, c.position());
        assertTrue(c.online());
        assertEquals(now, c.observedAt());
    }

    @Test
    void nullRoomWirdLeererString() {
        Cover c = new Cover("c1", "Store", null, 0, true, now);
        assertEquals("", c.room());
    }

    @Test
    void positionUnbekanntErlaubt() {
        Cover c = new Cover("c1", "Store", "Raum", Cover.POSITION_UNKNOWN, false, now);
        assertEquals(Cover.POSITION_UNKNOWN, c.position());
    }

    @Test
    void positionGrenzen0und100Erlaubt() {
        assertEquals(0, new Cover("c1", "Store", "Raum", 0, true, now).position());
        assertEquals(100, new Cover("c1", "Store", "Raum", 100, true, now).position());
    }

    @Test
    void idDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class, () -> new Cover(null, "Store", "Raum", 0, true, now));
        assertThrows(IllegalArgumentException.class, () -> new Cover(" ", "Store", "Raum", 0, true, now));
    }

    @Test
    void nameDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class, () -> new Cover("c1", " ", "Raum", 0, true, now));
    }

    @Test
    void positionUnterNullWirft() {
        assertThrows(IllegalArgumentException.class, () -> new Cover("c1", "Store", "Raum", -5, true, now));
    }

    @Test
    void positionUeber100Wirft() {
        assertThrows(IllegalArgumentException.class, () -> new Cover("c1", "Store", "Raum", 101, true, now));
    }

    @Test
    void observedAtDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class, () -> new Cover("c1", "Store", "Raum", 0, true, null));
    }

    @Test
    void onlineSetztFlag() {
        Cover c = Cover.online("c1", "Store", "Raum", 30, now);
        assertTrue(c.online());
        assertEquals(30, c.position());
    }

    @Test
    void offlineSetztFlag() {
        Cover c = Cover.offline("c1", "Store", "Raum", 70, now);
        assertFalse(c.online());
        assertEquals(70, c.position());
    }

    @Test
    void requireValidPositionAkzeptiertGrenzen() {
        assertEquals(0, Cover.requireValidPosition(0));
        assertEquals(100, Cover.requireValidPosition(100));
    }

    @Test
    void requireValidPositionWirftAusserhalb() {
        assertThrows(IllegalArgumentException.class, () -> Cover.requireValidPosition(-1));
        assertThrows(IllegalArgumentException.class, () -> Cover.requireValidPosition(101));
    }
}
