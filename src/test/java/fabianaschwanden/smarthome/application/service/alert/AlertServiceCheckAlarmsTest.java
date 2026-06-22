package fabianaschwanden.smarthome.application.service.alert;

import fabianaschwanden.smarthome.domain.model.alert.AlertSettings;
import fabianaschwanden.smarthome.domain.model.safety.AlarmState;
import fabianaschwanden.smarthome.domain.model.safety.SmokeDetector;
import fabianaschwanden.smarthome.domain.port.out.alert.AlertPublisher;
import fabianaschwanden.smarthome.domain.port.out.alert.AlertSettingsRepository;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Direkter Unit-Test der Flankenerkennung in {@link AlertService#checkAlarms()}
 * mit anonymen Port-Implementierungen (kein echtes Gerät, kein Netzwerk).
 */
@QuarkusTest
class AlertServiceCheckAlarmsTest {

    private static final AlertSettings ENABLED = new AlertSettings(true, "mein-topic");

    private static SmokeDetector detector(AlarmState alarm) {
        return SmokeDetector.online("sm-1", "Küche", "Küche", alarm, 90, Instant.now());
    }

    private static AlertSettingsRepository repo(AlertSettings settings) {
        return new AlertSettingsRepository() {
            @Override public Optional<AlertSettings> load() { return Optional.of(settings); }
            @Override public void save(AlertSettings s) { }
        };
    }

    private static AlertPublisher countingPublisher(AtomicInteger counter) {
        return (settings, title, message, priority) -> {
            counter.incrementAndGet();
            return true;
        };
    }

    @Test
    void pushtEinmalBeiFlankeUndNichtErneutSolangeAlarmAnhaelt() {
        AtomicInteger pushes = new AtomicInteger();
        AtomicReference<AlarmState> state = new AtomicReference<>(AlarmState.ALARM);
        AlertService service = new AlertService(
                repo(ENABLED),
                countingPublisher(pushes),
                () -> List.of(detector(state.get())));

        service.checkAlarms();
        assertEquals(1, pushes.get(), "OK->ALARM-Flanke pusht einmal");

        service.checkAlarms();
        service.checkAlarms();
        assertEquals(1, pushes.get(), "anhaltender Alarm pusht nicht erneut");
    }

    @Test
    void entwarnungMachtWiederScharf() {
        AtomicInteger pushes = new AtomicInteger();
        AtomicReference<AlarmState> state = new AtomicReference<>(AlarmState.ALARM);
        AlertService service = new AlertService(
                repo(ENABLED),
                countingPublisher(pushes),
                () -> List.of(detector(state.get())));

        service.checkAlarms();                 // Flanke -> Push 1
        state.set(AlarmState.OK);
        service.checkAlarms();                 // Entwarnung -> wieder scharf
        assertEquals(1, pushes.get());

        state.set(AlarmState.ALARM);
        service.checkAlarms();                 // neue Flanke -> Push 2
        assertEquals(2, pushes.get());
    }

    @Test
    void keinPushWennNichtSendebereit() {
        AtomicInteger pushes = new AtomicInteger();
        // enabled, aber kein Topic -> canPush() == false
        AlertService service = new AlertService(
                repo(new AlertSettings(true, "")),
                countingPublisher(pushes),
                () -> List.of(detector(AlarmState.ALARM)));

        service.checkAlarms();
        assertEquals(0, pushes.get());
    }

    @Test
    void keinPushWennDeaktiviert() {
        AtomicInteger pushes = new AtomicInteger();
        AlertService service = new AlertService(
                repo(AlertSettings.disabled()),
                countingPublisher(pushes),
                () -> List.of(detector(AlarmState.ALARM)));

        service.checkAlarms();
        assertEquals(0, pushes.get());
    }

    @Test
    void keinPushBeiEntwarntemMelder() {
        AtomicInteger pushes = new AtomicInteger();
        AlertService service = new AlertService(
                repo(ENABLED),
                countingPublisher(pushes),
                () -> List.of(detector(AlarmState.OK)));

        service.checkAlarms();
        assertEquals(0, pushes.get());
    }
}
