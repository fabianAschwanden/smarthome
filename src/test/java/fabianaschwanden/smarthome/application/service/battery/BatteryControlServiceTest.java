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
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatteryControlServiceTest {

    private final Instant now = Instant.parse("2026-06-19T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final SurplusChargePolicy policy = new SurplusChargePolicy(1500, 300);

    private final CapturingRelay relay = new CapturingRelay();
    private double smartfoxGridWatt;

    private BatteryControlService service() {
        BatteryControlService service = new BatteryControlService(
                energyWith(() -> smartfoxGridWatt), relay, policy, PowerSource.SMARTFOX, clock);
        service.applyInitialState();
        return service;
    }

    @Test
    void startetImManuellModusMitRelaisAus() {
        BatteryControlService service = service();
        assertEquals(ControlMode.MANUAL, service.status().mode());
        assertEquals(RelayState.OFF, service.status().desiredState());
        assertEquals(RelayState.OFF, relay.last());
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

        // 1x initial (OFF) + 1x echter Wechsel auf ON, kein zweiter Call.
        assertEquals(List.of(RelayState.OFF, RelayState.ON), relay.calls);
    }

    private CurrentEnergyQuery energyWith(java.util.function.DoubleSupplier grid) {
        return () -> new EnergySnapshot(
                now,
                List.of(PowerReading.of(PowerSource.SMARTFOX, now, grid.getAsDouble(), 0, null, 0)),
                Optional.empty());
    }

    private static final class CapturingRelay implements RelaySwitch {
        private final List<RelayState> calls = new ArrayList<>();

        @Override
        public void apply(RelayState state) {
            calls.add(state);
        }

        RelayState last() {
            return calls.getLast();
        }
    }
}
