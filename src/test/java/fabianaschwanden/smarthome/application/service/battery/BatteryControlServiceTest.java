package fabianaschwanden.smarthome.application.service.battery;

import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.port.in.battery.ManualSwitchNotAllowed;
import fabianaschwanden.smarthome.domain.port.out.battery.RelaySwitch;
import fabianaschwanden.smarthome.domain.service.battery.SurplusChargePolicy;
import fabianaschwanden.smarthome.domain.model.energy.EnergySnapshot;
import fabianaschwanden.smarthome.domain.model.energy.PowerReading;
import fabianaschwanden.smarthome.domain.model.energy.PowerSource;
import fabianaschwanden.smarthome.domain.port.in.energy.CurrentEnergyQuery;
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
    private final SurplusChargePolicy policy = new SurplusChargePolicy(1500, 300);

    private final CapturingRelay relay = new CapturingRelay();
    private double smartfoxGridWatt;

    private BatteryControlService service() {
        BatteryControlService service = new BatteryControlService(
                energyWith(() -> smartfoxGridWatt), relay, policy, PowerSource.SMARTFOX, clock);
        service.initFromDevice();
        return service;
    }

    @Test
    void startetNeutralOhneSchaltbefehl() {
        // Ist-Zustand nicht lesbar (Default-Relay liefert empty) -> Manuell/AUS, KEIN apply().
        BatteryControlService service = service();
        assertEquals(ControlMode.MANUAL, service.status().mode());
        assertEquals(RelayState.OFF, service.status().desiredState());
        assertTrue(relay.calls.isEmpty(), "Start darf keinen Schaltbefehl senden");
    }

    @Test
    void uebernimmtGelesenenIstZustandOhneSchaltbefehl() {
        // Relais meldet EIN -> Zustand wird übernommen, aber NICHT erneut geschaltet.
        relay.readState = Optional.of(RelayState.ON);
        BatteryControlService service = service();
        assertEquals(ControlMode.MANUAL, service.status().mode());
        assertEquals(RelayState.ON, service.status().desiredState());
        assertTrue(relay.calls.isEmpty(), "Start darf keinen Schaltbefehl senden");
    }

    @Test
    void manuellFuehrtVeralteteAnzeigeNach() {
        // Start: Relais liest EIN -> Anzeige EIN.
        relay.readState = Optional.of(RelayState.ON);
        BatteryControlService service = service();
        assertEquals(RelayState.ON, service.status().desiredState());

        // Relais wird extern (native View) auf AUS gestellt.
        relay.readState = Optional.of(RelayState.OFF);
        service.syncFromDevice();

        assertEquals(RelayState.OFF, service.status().desiredState(), "Anzeige muss dem Ist folgen");
        assertTrue(relay.calls.isEmpty(), "Nachführen darf nicht selbst schalten");
    }

    @Test
    void syncGreiftImAutoModusNichtEin() {
        smartfoxGridWatt = -2000;
        BatteryControlService service = service();
        service.changeMode(ControlMode.AUTO);
        service.autoTick(); // -> ON
        assertEquals(RelayState.ON, service.status().desiredState());

        // Ein einzelner Lese-Ausreisser darf im AUTO-Modus nichts überschreiben.
        relay.readState = Optional.of(RelayState.OFF);
        service.syncFromDevice();

        assertEquals(RelayState.ON, service.status().desiredState());
    }

    @Test
    void manuellHaeltStandBeiLesefehler() {
        relay.readState = Optional.of(RelayState.ON);
        BatteryControlService service = service();

        relay.readState = Optional.empty(); // Ist nicht lesbar
        service.syncFromDevice();

        assertEquals(RelayState.ON, service.status().desiredState(), "kein Flackern bei Lesefehler");
    }

    @Test
    void autoModusSchaltetBeiUeberschussEin() {
        smartfoxGridWatt = -2000; // 2000 W Einspeisung
        BatteryControlService service = service();
        service.changeMode(ControlMode.AUTO);

        service.autoTick();

        assertEquals(RelayState.ON, service.status().desiredState());
        assertEquals(RelayState.ON, relay.last());
    }

    @Test
    void autoModusSchaltetBeiBezugWiederAus() {
        smartfoxGridWatt = -2000;
        BatteryControlService service = service();
        service.changeMode(ControlMode.AUTO);
        service.autoTick();

        smartfoxGridWatt = 500; // Netzbezug -> Überschuss negativ
        service.autoTick();

        assertEquals(RelayState.OFF, service.status().desiredState());
    }

    @Test
    void manuellerModusIgnoriertAutoTick() {
        smartfoxGridWatt = -2000;
        BatteryControlService service = service();
        service.changeMode(ControlMode.MANUAL);

        service.autoTick();

        assertEquals(RelayState.OFF, service.status().desiredState());
    }

    @Test
    void manuellesSchaltenNurImManuellModus() {
        BatteryControlService service = service();

        // Im AUTO-Modus ist manuelles Schalten verboten.
        service.changeMode(ControlMode.AUTO);
        assertThrows(ManualSwitchNotAllowed.class, () -> service.switchRelay(RelayState.ON));

        service.changeMode(ControlMode.MANUAL);
        service.switchRelay(RelayState.ON);
        assertEquals(RelayState.ON, service.status().desiredState());
        assertEquals(RelayState.ON, relay.last());
    }

    @Test
    void schaltetNurBeiEchtemZustandswechsel() {
        smartfoxGridWatt = -2000;
        BatteryControlService service = service();
        service.changeMode(ControlMode.AUTO);

        service.autoTick();
        service.autoTick(); // unveränderter Überschuss

        // Kein Start-Befehl, dann genau 1 echter Wechsel auf ON, kein zweiter Call.
        assertEquals(List.of(RelayState.ON), relay.calls);
    }

    private CurrentEnergyQuery energyWith(java.util.function.DoubleSupplier grid) {
        return () -> new EnergySnapshot(
                now,
                List.of(PowerReading.of(PowerSource.SMARTFOX, now, grid.getAsDouble(), 0, null, 0)),
                Optional.empty());
    }

    private static final class CapturingRelay implements RelaySwitch {
        private final List<RelayState> calls = new ArrayList<>();
        private Optional<RelayState> readState = Optional.empty();

        @Override
        public void apply(RelayState state) {
            calls.add(state);
        }

        @Override
        public Optional<RelayState> read() {
            return readState;
        }

        RelayState last() {
            return calls.getLast();
        }
    }
}
