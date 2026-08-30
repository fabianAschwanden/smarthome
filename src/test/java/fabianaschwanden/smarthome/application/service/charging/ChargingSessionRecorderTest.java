package fabianaschwanden.smarthome.application.service.charging;

import fabianaschwanden.smarthome.domain.model.battery.BatteryControl;
import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.charging.ChargingSession;
import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
import fabianaschwanden.smarthome.domain.port.in.battery.ControlBattery;
import fabianaschwanden.smarthome.domain.port.out.charging.ChargingSessionRepository;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Erkennen von Ladevorgaengen - auch solchen, die am Geraet selbst gestartet wurden. */
class ChargingSessionRecorderTest {

    private static final Instant EIN = Instant.parse("2026-08-16T10:00:00Z");
    private static final Instant AUS = EIN.plus(Duration.ofHours(2));

    private FakeBattery battery;
    private FakeSessions sessions;
    private FakeSamples samples;
    private ChargingSessionRecorder recorder;

    @BeforeEach
    void setUp() {
        battery = new FakeBattery();
        sessions = new FakeSessions();
        samples = new FakeSamples();
        // Gegenmessung in den Grundtests aus: Sie hat ihre eigene Testklasse.
        recorder = new ChargingSessionRecorder(battery, sessions, samples,
                Duration.ofSeconds(60), Duration.ofMinutes(30), false, Duration.ofMinutes(7), Duration.ofSeconds(90));
    }

    @Test
    void haelt_den_beginn_beim_einschalten_fest() {
        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.ON, EIN);

        recorder.tick();

        assertEquals(EIN, sessions.open().orElseThrow().startedAt());
    }

    @Test
    void haelt_den_schaltzeitpunkt_fest_nicht_den_des_hinsehens() {
        // Ein extern gestarteter Ladevorgang wird erst beim naechsten Abgleich bemerkt;
        // gezaehlt wird trotzdem ab dem Schalten.
        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.ON, EIN);

        recorder.tick();
        recorder.tick();

        assertEquals(EIN, sessions.open().orElseThrow().startedAt());
        assertEquals(1, sessions.opened);
    }

    @Test
    void schliesst_den_vorgang_mit_der_geschaetzten_energie_ab() {
        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.ON, EIN);
        recorder.tick();
        samples.samples.addAll(reihe(EIN.minus(Duration.ofMinutes(2)), EIN, 400));
        samples.samples.addAll(reihe(EIN.plusSeconds(60), AUS, 2400));

        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.OFF, AUS);
        recorder.tick();

        ChargingSession session = sessions.closed.orElseThrow();
        assertEquals(2000.0, session.watt());
        assertEquals(4.0, session.energyKwh());
        assertTrue(sessions.open().isEmpty());
    }

    @Test
    void laesst_sich_von_einer_spitze_kurz_vor_dem_einschalten_nicht_taeuschen() {
        // Der Fall vom 29.08.2026: In den zwei Minuten vor dem Einschalten lief zufaellig
        // eine Haushaltsspitze (2570 W statt der sonst typischen 1980 W). Mit kurzen
        // Fenstern wurde die Differenz negativ, und negativ heisst 0 - ein Ladevorgang
        // ueber viereinhalb Stunden stand mit 0 kWh in der Liste.
        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.ON, EIN);
        recorder.tick();
        samples.samples.addAll(reihe(EIN.minus(Duration.ofMinutes(30)), EIN.minusSeconds(120), 1980));
        samples.samples.addAll(reihe(EIN.minusSeconds(120), EIN, 2570));   // die Spitze
        samples.samples.addAll(reihe(EIN.plusSeconds(60), AUS, 3620));     // waehrend des Ladens

        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.OFF, AUS);
        recorder.tick();

        ChargingSession session = sessions.closed.orElseThrow();
        assertEquals(1640.0, session.watt(), 60.0);   // 3620 minus rund 1980
        assertTrue(session.energyKwh() > 3.0, "Ladevorgang darf nicht mit 0 kWh enden");
    }

    @Test
    void verwirft_einen_vorgang_ohne_messpunkte() {
        // Lieber gar kein Eintrag als eine erfundene 0 - die saehe aus wie "nicht geladen".
        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.ON, EIN);
        recorder.tick();

        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.OFF, AUS);
        recorder.tick();

        assertTrue(sessions.closed.isEmpty());
        assertTrue(sessions.discarded);
        assertTrue(sessions.open().isEmpty());
    }

    @Test
    void faengt_nichts_an_solange_nicht_geladen_wird() {
        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.OFF, EIN);

        recorder.tick();

        assertTrue(sessions.open().isEmpty());
        assertFalse(sessions.discarded);
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
            throw new UnsupportedOperationException();
        }
    }

    static final class FakeSessions implements ChargingSessionRepository {
        private fabianaschwanden.smarthome.domain.model.charging.OpenChargingSession open;
        Optional<ChargingSession> closed = Optional.empty();
        private boolean discarded;
        private int opened;

        void setOpen(fabianaschwanden.smarthome.domain.model.charging.OpenChargingSession session) {
            open = session;
        }

        @Override
        public void open(Instant startedAt) {
            if (open == null) {
                open = fabianaschwanden.smarthome.domain.model.charging.OpenChargingSession
                        .startedAt(startedAt);
                opened++;
            }
        }

        @Override
        public Optional<fabianaschwanden.smarthome.domain.model.charging.OpenChargingSession> open() {
            return Optional.ofNullable(open);
        }

        @Override
        public void updateOpen(
                fabianaschwanden.smarthome.domain.model.charging.OpenChargingSession session) {
            open = session;
        }

        @Override
        public void close(ChargingSession session) {
            closed = Optional.of(session);
            open = null;
        }

        @Override
        public void discardOpen() {
            discarded = true;
            open = null;
        }

        @Override
        public List<ChargingSession> latest(int limit) {
            return closed.map(List::of).orElseGet(List::of);
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
        public java.util.OptionalDouble medianConsumptionBetween(Instant from, Instant to) {
            java.util.List<Double> werte = samples.stream()
                    .filter(s -> !s.timestamp().isBefore(from) && s.timestamp().isBefore(to))
                    .map(EnergySample::consumptionWatt)
                    .sorted()
                    .toList();
            if (werte.isEmpty()) {
                return java.util.OptionalDouble.empty();
            }
            return java.util.OptionalDouble.of(werte.get(werte.size() / 2));
        }


        @Override
        public long total() {
            return samples.size();
        }
    }
}
