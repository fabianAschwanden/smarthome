package fabianaschwanden.smarthome.application.service.batteryschedule;

import fabianaschwanden.smarthome.domain.model.battery.BatteryControl;
import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;
import fabianaschwanden.smarthome.domain.port.in.battery.ControlBattery;
import fabianaschwanden.smarthome.domain.port.out.batteryschedule.BatteryScheduleRepository;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deckt CRUD + Scheduler-Ausführung (tick/fire/isDue) der Batterie-Zeitsteuerung ab. */
@QuarkusTest
class BatteryScheduleServiceTest {

    private final ZoneId zone = ZoneId.of("Europe/Zurich");
    // Montag, 22:00 lokale Zeit.
    private final ZonedDateTime now = ZonedDateTime.of(2026, 6, 15, 22, 0, 0, 0, zone);
    private final Clock clock = Clock.fixed(now.toInstant(), zone);

    @Test
    void faelligerScheduleSetztManuellUndSchaltetRelais() {
        InMemoryRepo repo = new InMemoryRepo();
        repo.save(BatterySchedule.schedule(UUID.randomUUID(), RelayState.OFF, LocalTime.of(22, 0), Set.of()));
        RecordingBattery battery = new RecordingBattery();

        new BatteryScheduleService(repo, battery, clock).tick();

        assertEquals(List.of("MANUAL", "OFF"), battery.calls, "erst MANUAL, dann Relais");
    }

    @Test
    void nichtFaelligerScheduleTutNichts() {
        InMemoryRepo repo = new InMemoryRepo();
        repo.save(BatterySchedule.schedule(UUID.randomUUID(), RelayState.ON, LocalTime.of(6, 0), Set.of()));
        RecordingBattery battery = new RecordingBattery();

        new BatteryScheduleService(repo, battery, clock).tick();

        assertTrue(battery.calls.isEmpty());
    }

    @Test
    void countdownFeuertUndDeaktiviertSich() {
        InMemoryRepo repo = new InMemoryRepo();
        BatterySchedule cd = BatterySchedule.countdown(
                UUID.randomUUID(), RelayState.ON, now.toInstant().minusSeconds(1)); // bereits fällig
        repo.save(cd);
        RecordingBattery battery = new RecordingBattery();

        new BatteryScheduleService(repo, battery, clock).tick();

        assertEquals(List.of("MANUAL", "ON"), battery.calls);
        assertFalse(repo.byId(cd.id()).orElseThrow().enabled(), "COUNTDOWN deaktiviert sich");
    }

    @Test
    void setEnabledUndDelete() {
        InMemoryRepo repo = new InMemoryRepo();
        BatterySchedule s = BatterySchedule.schedule(UUID.randomUUID(), RelayState.ON, LocalTime.of(8, 0), Set.of());
        repo.save(s);
        BatteryScheduleService service = new BatteryScheduleService(repo, new RecordingBattery(), clock);

        service.setEnabled(s.id(), false);
        assertFalse(repo.byId(s.id()).orElseThrow().enabled());

        service.delete(s.id());
        assertTrue(repo.byId(s.id()).isEmpty());
    }

    // --- Fakes ---

    private static final class RecordingBattery implements ControlBattery {
        final List<String> calls = new ArrayList<>();
        private BatteryControl control = BatteryControl.initial(Instant.EPOCH);

        @Override public BatteryControl status() { return control; }

        @Override public BatteryControl changeMode(ControlMode mode) {
            calls.add(mode.name());
            control = control.withMode(mode, Instant.EPOCH);
            return control;
        }

        @Override public BatteryControl switchRelay(RelayState state) {
            calls.add(state.name());
            control = control.withState(state, Instant.EPOCH);
            return control;
        }
    }

    private static final class InMemoryRepo implements BatteryScheduleRepository {
        private final ConcurrentMap<UUID, BatterySchedule> store = new ConcurrentHashMap<>();

        @Override public BatterySchedule save(BatterySchedule s) { store.put(s.id(), s); return s; }
        @Override public Optional<BatterySchedule> byId(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<BatterySchedule> all() { return new ArrayList<>(store.values()); }
        @Override public List<BatterySchedule> allEnabled() {
            return store.values().stream().filter(BatterySchedule::enabled).toList();
        }
        @Override public void delete(UUID id) { store.remove(id); }
    }
}
