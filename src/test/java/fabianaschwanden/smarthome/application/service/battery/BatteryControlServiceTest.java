package fabianaschwanden.smarthome.application.service.battery;

import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.port.in.battery.ManualSwitchNotAllowed;
import fabianaschwanden.smarthome.domain.model.battery.RelayReading;
import fabianaschwanden.smarthome.domain.port.out.battery.RelaySwitch;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class BatteryControlServiceTest {

    private final Instant now = Instant.parse("2026-06-19T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final CapturingRelay relay = new CapturingRelay();

    private BatteryControlService service() {
        BatteryControlService service = new BatteryControlService(relay, clock);
        service.initFromDevice();
        return service;
    }

    @Test
    void startetNeutralOhneSchaltbefehl() {
        // Ist nicht lesbar (Default empty) -> Aus = (MANUAL, OFF), KEIN apply().
        BatteryControlService service = service();
        assertEquals(ControlMode.MANUAL, service.status().mode());
        assertEquals(RelayState.OFF, service.status().desiredState());
        assertTrue(relay.calls.isEmpty(), "Start darf keinen Schaltbefehl senden");
    }

    @Test
    void uebernimmtGelesenenIstZustandOhneSchaltbefehl() {
        relay.reading = Optional.of(new RelayReading(ControlMode.AUTO, RelayState.ON)); // Automatik/ladend
        BatteryControlService service = service();
        assertEquals(ControlMode.AUTO, service.status().mode());
        assertEquals(RelayState.ON, service.status().desiredState());
        assertTrue(relay.calls.isEmpty(), "Start darf keinen Schaltbefehl senden");
    }

    @Test
    void manuellEinAusUndAutomatikSchalten() {
        BatteryControlService service = service();

        // Manuell EIN
        service.switchRelay(RelayState.ON);
        assertEquals(ControlMode.MANUAL, service.status().mode());
        assertEquals(RelayState.ON, service.status().desiredState());
        assertEquals(new RelayReading(ControlMode.MANUAL, RelayState.ON), relay.last());

        // Manuell AUS
        service.switchRelay(RelayState.OFF);
        assertEquals(new RelayReading(ControlMode.MANUAL, RelayState.OFF), relay.last());

        // Automatik
        service.changeMode(ControlMode.AUTO);
        assertEquals(ControlMode.AUTO, service.status().mode());
        assertEquals(new RelayReading(ControlMode.AUTO, RelayState.OFF), relay.last());
    }

    @Test
    void manuellesSchaltenNurImManuellModus() {
        BatteryControlService service = service();

        service.changeMode(ControlMode.AUTO);
        assertThrows(ManualSwitchNotAllowed.class, () -> service.switchRelay(RelayState.ON));

        service.changeMode(ControlMode.MANUAL);
        service.switchRelay(RelayState.ON);
        assertEquals(RelayState.ON, service.status().desiredState());
    }

    @Test
    void syncFuehrtVeralteteAnzeigeNach() {
        relay.reading = Optional.of(new RelayReading(ControlMode.MANUAL, RelayState.ON));
        BatteryControlService service = service();
        assertEquals(RelayState.ON, service.status().desiredState());

        // Relais extern (native View) auf Aus gestellt.
        relay.reading = Optional.of(new RelayReading(ControlMode.MANUAL, RelayState.OFF));
        service.syncFromDevice();

        assertEquals(RelayState.OFF, service.status().desiredState(), "Anzeige muss dem Ist folgen");
        assertTrue(relay.calls.isEmpty(), "Nachführen darf nicht selbst schalten");
    }

    @Test
    void syncSpiegeltAutomatikSchaltvorgang() {
        relay.reading = Optional.of(new RelayReading(ControlMode.AUTO, RelayState.OFF)); // Auto, nicht ladend
        BatteryControlService service = service();
        assertEquals(RelayState.OFF, service.status().desiredState());

        // SMARTFOX-Automatik beginnt zu laden (hidR1Mode 0 -> 1).
        relay.reading = Optional.of(new RelayReading(ControlMode.AUTO, RelayState.ON));
        service.syncFromDevice();

        assertEquals(ControlMode.AUTO, service.status().mode());
        assertEquals(RelayState.ON, service.status().desiredState());
    }

    @Test
    void haeltStandBeiLesefehler() {
        relay.reading = Optional.of(new RelayReading(ControlMode.MANUAL, RelayState.ON));
        BatteryControlService service = service();

        relay.reading = Optional.empty();
        service.syncFromDevice();

        assertEquals(RelayState.ON, service.status().desiredState(), "kein Flackern bei Lesefehler");
    }

    @Test
    void schaltetNurBeiEchtemZustandswechsel() {
        BatteryControlService service = service();

        service.switchRelay(RelayState.ON);
        service.switchRelay(RelayState.ON); // unverändert

        assertEquals(List.of(new RelayReading(ControlMode.MANUAL, RelayState.ON)), relay.calls);
    }

    private static final class CapturingRelay implements RelaySwitch {
        private final List<RelayReading> calls = new ArrayList<>();
        private Optional<RelayReading> reading = Optional.empty();

        @Override
        public void apply(ControlMode mode, RelayState state) {
            calls.add(new RelayReading(mode, state));
        }

        @Override
        public Optional<RelayReading> read() {
            return reading;
        }

        RelayReading last() {
            return calls.getLast();
        }
    }
}
