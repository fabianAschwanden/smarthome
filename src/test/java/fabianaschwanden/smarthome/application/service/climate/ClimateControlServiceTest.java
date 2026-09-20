package fabianaschwanden.smarthome.application.service.climate;

import fabianaschwanden.smarthome.domain.model.climate.Climate;
import fabianaschwanden.smarthome.domain.model.climate.ClimateMode;
import fabianaschwanden.smarthome.domain.port.in.climate.ClimateNotFound;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateDevice;
import io.quarkus.test.junit.QuarkusTest;
import fabianaschwanden.smarthome.domain.port.in.climate.ClimateDeactivated;
import fabianaschwanden.smarthome.testsupport.InMemoryActivation;
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
class ClimateControlServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-19T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void einschaltenUndZurueckLesen() {
        FakeClimate k = new FakeClimate("klima");
        ClimateControlService service = new ClimateControlService(List.of(k), new InMemoryActivation(), clock);

        Climate result = service.setPower("klima", true);

        assertTrue(result.power());
        assertTrue(result.online());
    }

    @Test
    void modusSetzen() {
        FakeClimate k = new FakeClimate("klima");
        ClimateControlService service = new ClimateControlService(List.of(k), new InMemoryActivation(), clock);

        assertEquals(ClimateMode.COOL, service.setMode("klima", ClimateMode.COOL).mode());
    }

    @Test
    void sollTemperaturSetzen() {
        FakeClimate k = new FakeClimate("klima");
        ClimateControlService service = new ClimateControlService(List.of(k), new InMemoryActivation(), clock);

        assertEquals(24, service.setTargetTemp("klima", 24).targetTemp());
    }

    @Test
    void ungueltigeTemperaturWirdAbgelehnt() {
        ClimateControlService service = new ClimateControlService(List.of(new FakeClimate("klima")), new InMemoryActivation(), clock);
        assertThrows(IllegalArgumentException.class, () -> service.setTargetTemp("klima", 40));
    }

    @Test
    void unbekanntesGeraetWirftNotFound() {
        ClimateControlService service = new ClimateControlService(List.of(new FakeClimate("klima")), new InMemoryActivation(), clock);
        assertThrows(ClimateNotFound.class, () -> service.setPower("x", true));
    }

    @Test
    void aussentemperaturWirdDurchgereicht() {
        FakeClimate k = new FakeClimate("klima");
        ClimateControlService service = new ClimateControlService(List.of(k), new InMemoryActivation(), clock);

        assertEquals(12, service.list().get(0).outdoorTemp());
    }

    @Test
    void boostSetzen() {
        FakeClimate k = new FakeClimate("klima");
        ClimateControlService service = new ClimateControlService(List.of(k), new InMemoryActivation(), clock);

        assertTrue(service.setBoost("klima", true).boost());
        assertFalse(service.setBoost("klima", false).boost());
    }

    @Test
    void offlineMeldetLetztenZustand() {
        FakeClimate k = new FakeClimate("klima");
        ClimateControlService service = new ClimateControlService(List.of(k), new InMemoryActivation(), clock);
        service.setTargetTemp("klima", 25);

        k.reachable = false;
        Climate status = service.list().get(0);

        assertFalse(status.online());
        assertEquals(25, status.targetTemp());
    }

    @Test
    void stillgelegteAnlageWirdNichtMehrAngesprochen() {
        // Ueber den Winter vom Strom: nicht kaputt, sondern stillgelegt - und dann wird
        // sie auch nicht mehr abgefragt.
        FakeClimate k = new FakeClimate("klima");
        ClimateControlService service = new ClimateControlService(List.of(k), new InMemoryActivation(), clock);
        service.list();
        int vorher = k.reads;

        Climate stillgelegt = service.setActive("klima", false);
        service.list();
        service.list();

        assertFalse(stillgelegt.active());
        assertFalse(stillgelegt.online());
        assertEquals(vorher, k.reads, "eine stillgelegte Anlage wird nicht gelesen");
    }

    @Test
    void stillgelegteAnlageNimmtKeineBefehleAn() {
        FakeClimate k = new FakeClimate("klima");
        ClimateControlService service = new ClimateControlService(List.of(k), new InMemoryActivation(), clock);
        service.setActive("klima", false);

        assertThrows(ClimateDeactivated.class, () -> service.setPower("klima", true));
        assertThrows(ClimateDeactivated.class, () -> service.setMode("klima", ClimateMode.HEAT));
        assertThrows(ClimateDeactivated.class, () -> service.setTargetTemp("klima", 24));
        assertThrows(ClimateDeactivated.class, () -> service.setBoost("klima", true));
    }

    @Test
    void reaktivierteAnlageWirdWiederGelesen() {
        FakeClimate k = new FakeClimate("klima");
        ClimateControlService service = new ClimateControlService(List.of(k), new InMemoryActivation(), clock);
        service.setActive("klima", false);
        int vorher = k.reads;

        Climate zurueck = service.setActive("klima", true);

        assertTrue(zurueck.active());
        assertTrue(zurueck.online());
        assertTrue(k.reads > vorher);
    }

    @Test
    void unbekannteAnlageKannNichtStillgelegtWerden() {
        ClimateControlService service = new ClimateControlService(List.of(new FakeClimate("klima")), new InMemoryActivation(), clock);
        assertThrows(ClimateNotFound.class, () -> service.setActive("x", false));
    }

    private static final class FakeClimate implements ClimateDevice {
        private final String id;
        private boolean power = false;
        private boolean boost = false;
        private ClimateMode mode = ClimateMode.AUTO;
        private int target = 22;
        private boolean reachable = true;
        private int reads;

        FakeClimate(String id) {
            this.id = id;
        }

        @Override public String id() { return id; }
        @Override public String name() { return id; }
        @Override public String room() { return ""; }
        @Override public void applyPower(boolean on) { this.power = on; }
        @Override public void applyMode(ClimateMode mode) { this.mode = mode; }
        @Override public void applyTargetTemp(int temperature) { this.target = temperature; }
        @Override public void applyBoost(boolean on) { this.boost = on; }
        @Override public Optional<State> readState() {
            reads++;
            return reachable ? Optional.of(new State(power, boost, mode, target, 21, 12)) : Optional.empty();
        }
    }
}
