package fabianaschwanden.smarthome.domain.model.tuya;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TuyaSwitchTest {

    private final Instant now = Instant.parse("2026-06-22T10:00:00Z");

    @Test
    void gueltigeInstanzBautKorrekt() {
        TuyaSwitch s = new TuyaSwitch("s1", "Stehlampe", "Wohnzimmer", SwitchState.ON,
                true, false, "Hinweis", now);
        assertEquals("s1", s.id());
        assertEquals("Stehlampe", s.name());
        assertEquals("Wohnzimmer", s.room());
        assertEquals(SwitchState.ON, s.state());
        assertTrue(s.online());
        assertFalse(s.critical());
        assertEquals("Hinweis", s.hint());
        assertEquals(now, s.observedAt());
    }

    @Test
    void nullRoomWirdLeererString() {
        TuyaSwitch s = new TuyaSwitch("s1", "Lampe", null, SwitchState.OFF, true, false, "", now);
        assertEquals("", s.room());
    }

    @Test
    void nullHintWirdLeererString() {
        TuyaSwitch s = new TuyaSwitch("s1", "Lampe", "Raum", SwitchState.OFF, true, false, null, now);
        assertEquals("", s.hint());
    }

    @Test
    void idDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new TuyaSwitch(null, "Lampe", "Raum", SwitchState.ON, true, false, "", now));
        assertThrows(IllegalArgumentException.class,
                () -> new TuyaSwitch(" ", "Lampe", "Raum", SwitchState.ON, true, false, "", now));
    }

    @Test
    void nameDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new TuyaSwitch("s1", " ", "Raum", SwitchState.ON, true, false, "", now));
    }

    @Test
    void stateDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new TuyaSwitch("s1", "Lampe", "Raum", null, true, false, "", now));
    }

    @Test
    void observedAtDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new TuyaSwitch("s1", "Lampe", "Raum", SwitchState.ON, true, false, "", null));
    }

    @Test
    void onlineSetztFlag() {
        TuyaSwitch s = TuyaSwitch.online("s1", "Homecinema", "Raum", SwitchState.ON, true, "WLAN", now);
        assertTrue(s.online());
        assertTrue(s.critical());
        assertEquals("WLAN", s.hint());
        assertEquals(SwitchState.ON, s.state());
    }

    @Test
    void offlineSetztFlag() {
        TuyaSwitch s = TuyaSwitch.offline("s1", "Lampe", "Raum", SwitchState.OFF, false, "", now);
        assertFalse(s.online());
        assertEquals(SwitchState.OFF, s.state());
    }
}
