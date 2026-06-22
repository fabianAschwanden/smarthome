package fabianaschwanden.smarthome.application.service.schedule;

import fabianaschwanden.smarthome.domain.model.schedule.SwitchSchedule;
import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import fabianaschwanden.smarthome.domain.model.tuya.TuyaSwitch;
import fabianaschwanden.smarthome.domain.port.in.schedule.ScheduleNotFound;
import fabianaschwanden.smarthome.domain.port.in.tuya.ControlSwitches;
import fabianaschwanden.smarthome.domain.port.out.schedule.ScheduleRepository;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deckt die CRUD-Pfade (forSwitch/setEnabled/delete inkl. Not-Found) und die
 * Fehlerbehandlung im {@code tick} ab. Ergänzt {@link ScheduleServiceFireTest}.
 */
@QuarkusTest
class ScheduleServiceCrudTest {

    private final ZoneId zone = ZoneId.of("Europe/Zurich");
    private final ZonedDateTime now = ZonedDateTime.of(2026, 6, 15, 7, 0, 0, 0, zone);
    private final Clock clock = Clock.fixed(now.toInstant(), zone);

    private ScheduleService service(ScheduleRepository repo) {
        return new ScheduleService(repo, new NoopSwitches(), clock, new Random(1));
    }

    @Test
    void forSwitchLiefertGespeicherteRegeln() {
        InMemoryRepo repo = new InMemoryRepo();
        SwitchSchedule s = SwitchSchedule.schedule(
                UUID.randomUUID(), "lampe", SwitchState.ON, LocalTime.of(7, 0), java.util.Set.of());
        repo.save(s);

        List<SwitchSchedule> result = service(repo).forSwitch("lampe");

        assertEquals(1, result.size());
        assertEquals("lampe", result.get(0).switchId());
    }

    @Test
    void saveDelegiertAnRepository() {
        InMemoryRepo repo = new InMemoryRepo();
        SwitchSchedule s = SwitchSchedule.schedule(
                UUID.randomUUID(), "lampe", SwitchState.ON, LocalTime.of(7, 0), java.util.Set.of());

        SwitchSchedule saved = service(repo).save(s);

        assertEquals(s.id(), saved.id());
        assertTrue(repo.byId(s.id()).isPresent());
    }

    @Test
    void setEnabledSchaltetUmUndPersistiert() {
        InMemoryRepo repo = new InMemoryRepo();
        SwitchSchedule s = SwitchSchedule.schedule(
                UUID.randomUUID(), "lampe", SwitchState.ON, LocalTime.of(7, 0), java.util.Set.of());
        repo.save(s);

        SwitchSchedule result = service(repo).setEnabled(s.id(), false);

        assertFalse(result.enabled());
        assertFalse(repo.byId(s.id()).orElseThrow().enabled());
    }

    @Test
    void setEnabledAufUnbekannterIdWirftNotFound() {
        assertThrows(ScheduleNotFound.class,
                () -> service(new InMemoryRepo()).setEnabled(UUID.randomUUID(), true));
    }

    @Test
    void deleteEntferntVorhandeneRegel() {
        InMemoryRepo repo = new InMemoryRepo();
        SwitchSchedule s = SwitchSchedule.schedule(
                UUID.randomUUID(), "lampe", SwitchState.ON, LocalTime.of(7, 0), java.util.Set.of());
        repo.save(s);

        service(repo).delete(s.id());

        assertTrue(repo.byId(s.id()).isEmpty());
    }

    @Test
    void deleteAufUnbekannterIdWirftNotFound() {
        assertThrows(ScheduleNotFound.class,
                () -> service(new InMemoryRepo()).delete(UUID.randomUUID()));
    }

    @Test
    void tickFaengtFehlerEinerRegelAbUndLaeuftWeiter() {
        // Repository, das beim Schalten eine Ausnahme provoziert -> tick muss sie schlucken.
        InMemoryRepo repo = new InMemoryRepo();
        SwitchSchedule s = SwitchSchedule.schedule(
                UUID.randomUUID(), "lampe", SwitchState.ON, LocalTime.of(7, 0), java.util.Set.of());
        repo.save(s);
        ScheduleService service = new ScheduleService(repo, new FailingSwitches(), clock, new Random(1));

        // Wirft NICHT nach aussen, der catch-Zweig fängt den Fehler.
        service.tick();
    }

    private static final class InMemoryRepo implements ScheduleRepository {
        private final java.util.Map<UUID, SwitchSchedule> store = new ConcurrentHashMap<>();

        @Override public SwitchSchedule save(SwitchSchedule s) {
            store.put(s.id(), s);
            return s;
        }
        @Override public Optional<SwitchSchedule> byId(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<SwitchSchedule> forSwitch(String switchId) {
            return store.values().stream().filter(s -> s.switchId().equals(switchId)).toList();
        }
        @Override public List<SwitchSchedule> allEnabled() {
            return store.values().stream().filter(SwitchSchedule::enabled).toList();
        }
        @Override public void delete(UUID id) { store.remove(id); }
    }

    private static final class NoopSwitches implements ControlSwitches {
        @Override public List<TuyaSwitch> list() { return List.of(); }
        @Override public TuyaSwitch switchTo(String id, SwitchState state, boolean confirmed) {
            return TuyaSwitch.online(id, id, "", state, false, "", Instant.now());
        }
    }

    private static final class FailingSwitches implements ControlSwitches {
        @Override public List<TuyaSwitch> list() { return List.of(); }
        @Override public TuyaSwitch switchTo(String id, SwitchState state, boolean confirmed) {
            throw new IllegalStateException("Schalter nicht erreichbar");
        }
    }
}
