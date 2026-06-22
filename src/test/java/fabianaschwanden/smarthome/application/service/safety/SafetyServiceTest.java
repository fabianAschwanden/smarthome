package fabianaschwanden.smarthome.application.service.safety;

import fabianaschwanden.smarthome.domain.model.safety.AlarmState;
import fabianaschwanden.smarthome.domain.model.safety.SmokeDetector;
import fabianaschwanden.smarthome.domain.port.out.safety.SmokeDetectorDevice;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SafetyServiceTest {

    /** Steuerbare Uhr, damit das 5-Minuten-Fenster deterministisch testbar ist. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-06-20T12:00:00Z");

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    @Test
    void erreichbarIstOnline() {
        FakeSmoke s = new FakeSmoke(AlarmState.OK, 90);
        SafetyService service = new SafetyService(List.of(s), new MovableClock());

        SmokeDetector d = service.smokeDetectors().get(0);

        assertTrue(d.online());
        assertEquals(AlarmState.OK, d.alarm());
        assertEquals(90, d.battery());
    }

    @Test
    void kurzeLueckeBleibtOnlineMitLetztemZustand() {
        MovableClock clock = new MovableClock();
        FakeSmoke s = new FakeSmoke(AlarmState.OK, 80);
        SafetyService service = new SafetyService(List.of(s), clock);

        service.smokeDetectors(); // erste erfolgreiche Abfrage -> lastSeen gesetzt
        s.reachable = false;
        clock.advance(Duration.ofMinutes(3)); // < 5 min

        SmokeDetector d = service.smokeDetectors().get(0);
        assertTrue(d.online(), "innerhalb der 5-Min-Toleranz weiterhin online");
        assertEquals(80, d.battery());
    }

    @Test
    void laengerAlsFuenfMinutenWirdOffline() {
        MovableClock clock = new MovableClock();
        FakeSmoke s = new FakeSmoke(AlarmState.OK, 80);
        SafetyService service = new SafetyService(List.of(s), clock);

        service.smokeDetectors();
        s.reachable = false;
        clock.advance(Duration.ofMinutes(6)); // > 5 min

        SmokeDetector d = service.smokeDetectors().get(0);
        assertFalse(d.online(), "nach >5 Min ohne Kontakt offline");
        assertEquals(80, d.battery(), "letzter bekannter Wert bleibt erhalten");
    }

    @Test
    void alarmZustandWirdGemeldet() {
        FakeSmoke s = new FakeSmoke(AlarmState.ALARM, 70);
        SafetyService service = new SafetyService(List.of(s), new MovableClock());

        SmokeDetector d = service.smokeDetectors().get(0);

        assertTrue(d.online());
        assertEquals(AlarmState.ALARM, d.alarm());
        assertEquals(70, d.battery());
    }

    @Test
    void nieErreichbarIstOffline() {
        FakeSmoke s = new FakeSmoke(AlarmState.OK, 0);
        s.reachable = false;
        SafetyService service = new SafetyService(List.of(s), new MovableClock());

        assertFalse(service.smokeDetectors().get(0).online());
    }

    private static final class FakeSmoke implements SmokeDetectorDevice {
        private final AtomicReference<Reading> reading = new AtomicReference<>();
        private boolean reachable = true;

        FakeSmoke(AlarmState alarm, int battery) {
            reading.set(new Reading(alarm, battery));
        }

        @Override public String id() { return "rm"; }
        @Override public String name() { return "Rauchmelder"; }
        @Override public String room() { return "Wohnzimmer"; }
        @Override public Optional<Reading> read() {
            return reachable ? Optional.of(reading.get()) : Optional.empty();
        }
    }
}
