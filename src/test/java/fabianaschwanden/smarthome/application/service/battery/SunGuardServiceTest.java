package fabianaschwanden.smarthome.application.service.battery;

import fabianaschwanden.smarthome.domain.model.battery.BatteryControl;
import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.battery.SunGuard;
import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
import fabianaschwanden.smarthome.domain.port.in.battery.ControlBattery;
import fabianaschwanden.smarthome.domain.port.in.battery.ManualSwitchNotAllowed;
import fabianaschwanden.smarthome.domain.port.out.battery.SunGuardRepository;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Der Ohne-Sonne-Ausschalter: schaltet er ab, und schaltet er nur einmal ab? */
class SunGuardServiceTest {

    private static final Instant JETZT = Instant.parse("2026-09-13T16:20:00Z");
    private static final Clock UHR = Clock.fixed(JETZT, ZoneOffset.UTC);
    private static final Duration FENSTER = Duration.ofMinutes(20);

    private FakeGuardRepository guards;
    private FakeSamples samples;
    private FakeBattery battery;
    private SunGuardService service;

    @BeforeEach
    void setUp() {
        guards = new FakeGuardRepository();
        samples = new FakeSamples();
        battery = new FakeBattery();
        service = new SunGuardService(guards, samples, battery, UHR, FENSTER, 1000, 300);
    }

    @Test
    void ist_standardmaessig_aus() {
        assertFalse(service.status().enabled());
    }

    @Test
    void schaltet_nicht_solange_er_aus_ist() {
        battery.laedt();
        samples.pv(0);

        service.evaluate();

        assertEquals(RelayState.ON, battery.status().desiredState());
    }

    @Test
    void schaltet_die_ladung_ab_wenn_die_sonne_weg_ist() {
        service.setEnabled(true);
        scharfStellen();
        battery.laedt();
        samples.pv(80);

        service.evaluate();

        assertEquals(ControlMode.MANUAL, battery.status().mode());
        assertEquals(RelayState.OFF, battery.status().desiredState());
        assertNotNull(guards.guard.lastTrippedAt());
    }

    @Test
    void schaltet_auch_aus_dem_automatik_modus_ab() {
        // Wer den Waechter einschaltet, will die Ladung abends aus haben - nicht dem
        // Geraet ueberlassen. Ohne den Moduswechsel wuerde switchRelay abgelehnt.
        service.setEnabled(true);
        scharfStellen();
        battery.control = new BatteryControl(ControlMode.AUTO, RelayState.ON, JETZT);
        samples.pv(80);

        service.evaluate();

        assertEquals(ControlMode.MANUAL, battery.status().mode());
        assertEquals(RelayState.OFF, battery.status().desiredState());
    }

    @Test
    void schaltet_nach_dem_ausloesen_nicht_erneut_ab() {
        service.setEnabled(true);
        scharfStellen();
        battery.laedt();
        samples.pv(80);
        service.evaluate();

        // Jemand schaltet nachts bewusst ein - das darf der Waechter nicht abwuergen.
        battery.laedt();
        service.evaluate();

        assertEquals(RelayState.ON, battery.status().desiredState());
    }

    @Test
    void wird_erst_mit_der_naechsten_sonne_wieder_scharf() {
        service.setEnabled(true);
        scharfStellen();
        battery.laedt();
        samples.pv(80);
        service.evaluate();
        assertFalse(guards.guard.armed());

        samples.pv(2400);
        service.evaluate();
        assertTrue(guards.guard.armed());

        battery.laedt();
        samples.pv(80);
        service.evaluate();
        assertEquals(RelayState.OFF, battery.status().desiredState());
    }

    @Test
    void haelt_keinen_ausloesezeitpunkt_fest_wenn_ohnehin_schon_aus_ist() {
        // Sonst behauptete die Anzeige ein Schalten, das nie stattgefunden hat.
        service.setEnabled(true);
        scharfStellen();
        samples.pv(80);

        service.evaluate();

        assertNull(guards.guard.lastTrippedAt());
        assertFalse(guards.guard.armed());
        assertEquals(0, battery.schaltungen);
    }

    @Test
    void schaltet_ohne_messwerte_nicht_ab() {
        service.setEnabled(true);
        scharfStellen();
        battery.laedt();
        samples.leer();

        service.evaluate();

        assertEquals(RelayState.ON, battery.status().desiredState());
    }

    /** Ein Durchgang mit Sonne macht den Waechter scharf - so wie am Morgen. */
    private void scharfStellen() {
        samples.pv(2400);
        service.evaluate();
        assertTrue(guards.guard.armed());
    }

    // --- Fakes ---------------------------------------------------------------

    private static final class FakeGuardRepository implements SunGuardRepository {
        private SunGuard guard = SunGuard.disabled();

        @Override public SunGuard load() {
            return guard;
        }

        @Override public void save(SunGuard updated) {
            this.guard = updated;
        }
    }

    /** Liefert einen festen Median, ohne Messpunkte zu erfinden. */
    private static final class FakeSamples implements EnergySampleRepository {
        private OptionalDouble median = OptionalDouble.empty();

        void pv(double watt) {
            median = OptionalDouble.of(watt);
        }

        void leer() {
            median = OptionalDouble.empty();
        }

        @Override public OptionalDouble medianPvBetween(Instant from, Instant to) {
            return median;
        }

        @Override public void save(EnergySample sample) { }

        @Override public List<EnergySample> between(Instant from, Instant to) {
            return new ArrayList<>();
        }

        @Override public long deleteOlderThan(Instant cutoff) {
            return 0;
        }

        @Override public OptionalDouble medianConsumptionBetween(Instant from, Instant to) {
            return OptionalDouble.empty();
        }

        @Override public long total() {
            return 0;
        }
    }

    /** Bildet die echte Sperre nach: im AUTO-Modus wird nicht von Hand geschaltet. */
    private static final class FakeBattery implements ControlBattery {
        private BatteryControl control = BatteryControl.initial(JETZT);
        private int schaltungen;

        void laedt() {
            control = new BatteryControl(ControlMode.MANUAL, RelayState.ON, JETZT);
        }

        @Override public BatteryControl status() {
            return control;
        }

        @Override public BatteryControl changeMode(ControlMode mode) {
            control = control.withMode(mode, JETZT);
            return control;
        }

        @Override public BatteryControl switchRelay(RelayState state) {
            if (control.mode() != ControlMode.MANUAL) {
                throw new ManualSwitchNotAllowed();
            }
            if (control.desiredState() != state) {
                schaltungen++;
            }
            control = control.withState(state, JETZT);
            return control;
        }
    }
}
