package fabianaschwanden.smarthome.domain.model.appliance;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class TemperatureTest {

    @Test
    void gueltigeInstanzBautKorrekt() {
        Temperature t = new Temperature(36, 35, 20, 40);
        assertEquals(36, t.target());
        assertEquals(35, t.current());
        assertEquals(20, t.min());
        assertEquals(40, t.max());
    }

    @Test
    void currentUnknownErlaubt() {
        Temperature t = new Temperature(30, Temperature.UNKNOWN, 20, 40);
        assertEquals(Temperature.UNKNOWN, t.current());
    }

    @Test
    void minUeberMaxWirft() {
        assertThrows(IllegalArgumentException.class, () -> new Temperature(30, 30, 40, 20));
    }

    @Test
    void targetUnterMinWirft() {
        assertThrows(IllegalArgumentException.class, () -> new Temperature(19, 25, 20, 40));
    }

    @Test
    void targetUeberMaxWirft() {
        assertThrows(IllegalArgumentException.class, () -> new Temperature(41, 25, 20, 40));
    }

    @Test
    void withTargetLiefertNeueInstanz() {
        Temperature t = new Temperature(30, 25, 20, 40);
        Temperature t2 = t.withTarget(35);
        assertEquals(35, t2.target());
        assertEquals(25, t2.current());
        assertEquals(30, t.target()); // Original unverändert
    }

    @Test
    void withCurrentLiefertNeueInstanz() {
        Temperature t = new Temperature(30, 25, 20, 40);
        Temperature t2 = t.withCurrent(28);
        assertEquals(28, t2.current());
        assertEquals(30, t2.target());
        assertEquals(25, t.current()); // Original unverändert
    }

    @Test
    void requireInRangeAkzeptiertGrenzen() {
        Temperature t = new Temperature(30, 25, 20, 40);
        assertEquals(20, t.requireInRange(20));
        assertEquals(40, t.requireInRange(40));
    }

    @Test
    void requireInRangeWirftAusserhalb() {
        Temperature t = new Temperature(30, 25, 20, 40);
        assertThrows(IllegalArgumentException.class, () -> t.requireInRange(19));
        assertThrows(IllegalArgumentException.class, () -> t.requireInRange(41));
    }
}
