package fabianaschwanden.smarthome.application.service.charging;

import fabianaschwanden.smarthome.domain.model.battery.BatteryControl;
import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.charging.OpenChargingSession;
import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
import fabianaschwanden.smarthome.domain.port.in.battery.ControlBattery;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Gegenmessung: kurz abschalten, Abfall messen, wieder einschalten.
 *
 * <p>Sie fasst die Anlage physisch an – entsprechend genau muss geprüft werden, wann sie
 * das darf und dass sie nie ausgeschaltet zurücklässt.
 */
class ChargingVerificationTest {

    private static final Duration NACH = Duration.ofMinutes(7);
    private static final Duration PAUSE = Duration.ofSeconds(90);

    private FakeBattery battery;
    private ChargingSessionRecorderTest.FakeSessions sessions;
    private FakeSamples samples;

    @BeforeEach
    void setUp() {
        battery = new FakeBattery();
        sessions = new ChargingSessionRecorderTest.FakeSessions();
        samples = new FakeSamples();
    }

    /** Uhr auf JETZT - die Tests rechnen relativ dazu. */
    private ChargingSessionRecorder recorder(boolean enabled) {
        return recorder(enabled, java.time.Clock.systemUTC());
    }

    private ChargingSessionRecorder recorder(boolean enabled, java.time.Clock clock) {
        return new ChargingSessionRecorder(battery, sessions, samples,
                Duration.ofSeconds(60), Duration.ofMinutes(2), enabled, NACH, PAUSE, clock);
    }

    @Test
    void schaltet_erst_nach_der_wartezeit_ab() {
        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.ON, Instant.now());
        recorder(true).tick();   // legt den Vorgang an, gerade erst begonnen

        recorder(true).tick();

        assertEquals(RelayState.ON, battery.control.desiredState());
        assertFalse(sessions.open().orElseThrow().isPaused());
    }

    @Test
    void schaltet_nach_der_wartezeit_fuer_die_gegenmessung_ab() {
        Instant vorbei = Instant.now().minus(NACH).minusSeconds(30);
        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.ON, vorbei);
        ChargingSessionRecorder recorder = recorder(true);
        recorder.tick();   // Vorgang anlegen

        recorder.tick();   // faellig

        assertEquals(RelayState.OFF, battery.control.desiredState());
        assertTrue(sessions.open().orElseThrow().isPaused());
    }

    @Test
    void misst_auch_dann_gegen_wenn_das_relais_zwischendurch_gestellt_wurde() {
        // Der Fehler, der in Produktion auffiel: Als "jetzt" diente der Zeitpunkt der
        // letzten Relais-Aenderung. Nach einem Neustart liegt der HINTER dem Beginn des
        // Ladevorgangs - und stand still, solange nichts geschaltet wurde. Die
        // Gegenmessung wurde deshalb nie faellig.
        Instant begonnen = Instant.now().minus(NACH).minusSeconds(600);
        sessions.setOpen(OpenChargingSession.startedAt(begonnen));
        // Relais zuletzt NACH dem Sessionbeginn gestellt (z. B. Abgleich nach Neustart):
        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.ON, begonnen.plusSeconds(300));

        recorder(true).tick();

        assertEquals(RelayState.OFF, battery.control.desiredState());
        assertTrue(sessions.open().orElseThrow().isPaused());
    }

    @Test
    void haelt_die_pause_nicht_fuer_ein_ende_des_ladevorgangs() {
        // Ohne diesen Fall wuerde die eigene Pause als "fertig geladen" verbucht.
        Instant vorbei = Instant.now().minus(NACH).minusSeconds(30);
        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.ON, vorbei);
        ChargingSessionRecorder recorder = recorder(true);
        recorder.tick();
        recorder.tick();   // Pause laeuft, Relais AUS

        recorder.tick();

        assertTrue(sessions.open().isPresent());
        assertTrue(sessions.closed.isEmpty());
    }

    @Test
    void schaltet_nach_einem_neustart_mitten_in_der_pause_wieder_ein() {
        // Der Stand steht in der Datenbank, nicht im Speicher - sonst bliebe die Anlage
        // ausgeschaltet zurueck.
        Instant pausiert = Instant.now().minus(PAUSE).minusSeconds(10);
        sessions.setOpen(new OpenChargingSession(
                pausiert.minus(NACH), java.util.Optional.of(pausiert), java.util.Optional.empty()));
        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.OFF, pausiert);

        recorder(true).tick();   // frischer Recorder, wie nach einem Neustart

        assertEquals(RelayState.ON, battery.control.desiredState());
        assertTrue(sessions.open().orElseThrow().verificationDone());
    }

    @Test
    void ruehrt_das_relais_im_automatik_modus_nicht_an() {
        // Dort gehoert es dem SMARTFOX; ein Eingriff wuerde gegen dessen Regelung arbeiten.
        Instant vorbei = Instant.now().minus(NACH).minusSeconds(30);
        battery.control = new BatteryControl(ControlMode.AUTO, RelayState.ON, vorbei);
        ChargingSessionRecorder recorder = recorder(true);
        recorder.tick();

        recorder.tick();

        assertEquals(RelayState.ON, battery.control.desiredState());
        assertFalse(sessions.open().orElseThrow().isPaused());
    }

    @Test
    void unterbleibt_wenn_sie_abgeschaltet_ist() {
        Instant vorbei = Instant.now().minus(NACH).minusSeconds(30);
        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.ON, vorbei);
        ChargingSessionRecorder recorder = recorder(false);
        recorder.tick();

        recorder.tick();

        assertEquals(RelayState.ON, battery.control.desiredState());
    }

    @Test
    void nimmt_die_gegenmessung_fuer_die_energie() {
        // Sprung beim Einschalten 1800 W, Gegenmessung 2000 W -> es zaehlt die zweite.
        Instant ein = Instant.now().minus(Duration.ofHours(2));
        Instant pausiert = ein.plus(NACH);
        Instant weiter = pausiert.plus(PAUSE);
        Instant aus = ein.plus(Duration.ofHours(2));

        samples.samples.addAll(reihe(ein.minus(Duration.ofMinutes(2)), ein, 400));
        samples.samples.addAll(reihe(ein.plusSeconds(60), pausiert, 2200));
        samples.samples.addAll(reihe(pausiert.plusSeconds(60), weiter, 200));
        samples.samples.addAll(reihe(weiter, aus, 2200));

        sessions.setOpen(new OpenChargingSession(
                ein, java.util.Optional.of(pausiert), java.util.Optional.of(weiter)));
        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.OFF, aus);

        recorder(true).tick();

        assertEquals(2000.0, sessions.closed.orElseThrow().verifiedWatt().getAsDouble());
    }

    private static List<EnergySample> reihe(Instant von, Instant bis, double watt) {
        List<EnergySample> out = new ArrayList<>();
        for (Instant t = von; t.isBefore(bis); t = t.plusSeconds(10)) {
            out.add(new EnergySample(t, 0, watt));
        }
        return out;
    }

    private static final class FakeBattery implements ControlBattery {
        private BatteryControl control;

        @Override
        public BatteryControl status() {
            return control;
        }

        @Override
        public BatteryControl changeMode(ControlMode mode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BatteryControl switchRelay(RelayState state) {
            control = new BatteryControl(control.mode(), state, Instant.now());
            return control;
        }
    }

    private static final class FakeSamples implements EnergySampleRepository {
        private final List<EnergySample> samples = new ArrayList<>();

        @Override
        public void save(EnergySample sample) {
            samples.add(sample);
        }

        @Override
        public List<EnergySample> between(Instant fromInclusive, Instant toExclusive) {
            return samples;
        }

        @Override
        public long deleteOlderThan(Instant cutoff) {
            return 0;
        }

        @Override
        public long total() {
            return samples.size();
        }
    }
}
