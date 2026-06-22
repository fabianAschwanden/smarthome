package fabianaschwanden.smarthome.application.service.tuya;

import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import fabianaschwanden.smarthome.domain.model.tuya.TuyaSwitch;
import fabianaschwanden.smarthome.domain.port.in.tuya.CriticalConfirmationRequired;
import fabianaschwanden.smarthome.domain.port.in.tuya.SwitchNotFound;
import fabianaschwanden.smarthome.domain.port.out.tuya.SwitchDevice;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SwitchControlServiceTest {

    private final Instant now = Instant.parse("2026-06-19T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @Test
    void listetAlleGeraete() {
        SwitchControlService service = new SwitchControlService(
                List.of(new FakeDevice("a", "A", false), new FakeDevice("b", "B", false)), clock);

        List<TuyaSwitch> list = service.list();

        assertEquals(2, list.size());
        assertEquals("a", list.get(0).id());
        assertEquals("b", list.get(1).id());
    }

    @Test
    void schaltetGezieltUndLiestZurueck() {
        FakeDevice a = new FakeDevice("a", "A", false);
        SwitchControlService service = new SwitchControlService(List.of(a, new FakeDevice("b", "B", false)), clock);

        TuyaSwitch result = service.switchTo("a", SwitchState.ON, false);

        assertEquals("a", result.id());
        assertEquals(SwitchState.ON, result.state());
        assertTrue(result.online());
        assertEquals(SwitchState.ON, a.applied);
    }

    @Test
    void unbekannteIdWirftNotFound() {
        SwitchControlService service = new SwitchControlService(List.of(new FakeDevice("a", "A", false)), clock);
        assertThrows(SwitchNotFound.class, () -> service.switchTo("x", SwitchState.ON, false));
    }

    @Test
    void meldetOfflineMitLetztemZustand() {
        FakeDevice a = new FakeDevice("a", "A", false);
        SwitchControlService service = new SwitchControlService(List.of(a), clock);
        service.switchTo("a", SwitchState.ON, false);

        a.reachable = false;
        TuyaSwitch status = service.list().get(0);

        assertFalse(status.online());
        assertEquals(SwitchState.ON, status.state());
    }

    @Test
    void kritischerSchalterAusOhneBestaetigungWirdAbgelehnt() {
        FakeDevice critical = new FakeDevice("hc", "Homecinema", true);
        SwitchControlService service = new SwitchControlService(List.of(critical), clock);

        // EIN ist auch bei kritisch ohne Bestätigung erlaubt.
        service.switchTo("hc", SwitchState.ON, false);
        assertEquals(SwitchState.ON, critical.applied);

        // AUS ohne Bestätigung -> abgelehnt, Gerät bleibt EIN.
        assertThrows(CriticalConfirmationRequired.class, () -> service.switchTo("hc", SwitchState.OFF, false));
        assertEquals(SwitchState.ON, critical.applied);

        // AUS mit Bestätigung -> ausgeführt.
        service.switchTo("hc", SwitchState.OFF, true);
        assertEquals(SwitchState.OFF, critical.applied);
    }

    @Test
    void kritischFlagWirdImStatusGemeldet() {
        SwitchControlService service = new SwitchControlService(List.of(new FakeDevice("hc", "HC", true)), clock);
        assertTrue(service.list().get(0).critical());
    }

    private static final class FakeDevice implements SwitchDevice {
        private final String id;
        private final String name;
        private final boolean critical;
        private SwitchState applied = SwitchState.OFF;
        private boolean reachable = true;

        FakeDevice(String id, String name, boolean critical) {
            this.id = id;
            this.name = name;
            this.critical = critical;
        }

        @Override public String id() { return id; }
        @Override public String name() { return name; }
        @Override public String room() { return ""; }
        @Override public boolean critical() { return critical; }
        @Override public void apply(SwitchState state) { applied = state; }
        @Override public Optional<SwitchState> readState() {
            return reachable ? Optional.of(applied) : Optional.empty();
        }
    }
}
