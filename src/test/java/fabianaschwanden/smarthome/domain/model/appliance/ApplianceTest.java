package fabianaschwanden.smarthome.domain.model.appliance;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ApplianceTest {

    private final Instant now = Instant.parse("2026-06-22T10:00:00Z");

    private Map<ApplianceFunction, FunctionState> functions() {
        Map<ApplianceFunction, FunctionState> m = new HashMap<>();
        m.put(ApplianceFunction.PUMP, FunctionState.ON);
        m.put(ApplianceFunction.HEATER, FunctionState.OFF);
        return m;
    }

    @Test
    void gueltigeInstanzBautKorrekt() {
        Temperature t = new Temperature(36, 35, 20, 40);
        Appliance a = new Appliance("a1", "Whirlpool", "Wellness", true, now, functions(), t);
        assertEquals("a1", a.id());
        assertEquals("Whirlpool", a.name());
        assertEquals("Wellness", a.room());
        assertTrue(a.online());
        assertEquals(now, a.observedAt());
        assertEquals(t, a.temperature());
        assertEquals(FunctionState.ON, a.functions().get(ApplianceFunction.PUMP));
    }

    @Test
    void nullRoomWirdLeererString() {
        Appliance a = new Appliance("a1", "Pool", null, true, now, functions(), null);
        assertEquals("", a.room());
    }

    @Test
    void temperaturDarfNullSein() {
        Appliance a = new Appliance("a1", "Pool", "Raum", true, now, functions(), null);
        assertEquals(null, a.temperature());
    }

    @Test
    void idDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new Appliance(null, "Pool", "Raum", true, now, functions(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new Appliance(" ", "Pool", "Raum", true, now, functions(), null));
    }

    @Test
    void nameDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new Appliance("a1", " ", "Raum", true, now, functions(), null));
    }

    @Test
    void observedAtDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new Appliance("a1", "Pool", "Raum", true, null, functions(), null));
    }

    @Test
    void functionsDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new Appliance("a1", "Pool", "Raum", true, now, null, null));
    }

    @Test
    void functionsDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new Appliance("a1", "Pool", "Raum", true, now, Map.of(), null));
    }

    @Test
    void functionsSindUnveraenderbar() {
        Appliance a = new Appliance("a1", "Pool", "Raum", true, now, functions(), null);
        assertThrows(UnsupportedOperationException.class,
                () -> a.functions().put(ApplianceFunction.LIGHT, FunctionState.ON));
    }

    @Test
    void hasPrueftVorhandeneFunktion() {
        Appliance a = new Appliance("a1", "Pool", "Raum", true, now, functions(), null);
        assertTrue(a.has(ApplianceFunction.PUMP));
        assertTrue(a.has(ApplianceFunction.HEATER));
        assertFalse(a.has(ApplianceFunction.LIGHT));
    }
}
