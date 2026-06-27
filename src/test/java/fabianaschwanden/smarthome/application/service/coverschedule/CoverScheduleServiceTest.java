package fabianaschwanden.smarthome.application.service.coverschedule;

import fabianaschwanden.smarthome.domain.model.cover.Cover;
import fabianaschwanden.smarthome.domain.model.cover.CoverCommand;
import fabianaschwanden.smarthome.domain.model.coverschedule.CoverSchedule;
import fabianaschwanden.smarthome.domain.port.in.cover.ControlCovers;
import fabianaschwanden.smarthome.domain.port.out.coverschedule.CoverScheduleRepository;
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

/** Deckt CRUD + Scheduler-Ausführung (tick/fire/isDue) der Storen-Zeitsteuerung ab. */
@QuarkusTest
class CoverScheduleServiceTest {

    private final ZoneId zone = ZoneId.of("Europe/Zurich");
    // Montag, 06:30 lokale Zeit.
    private final ZonedDateTime now = ZonedDateTime.of(2026, 6, 15, 6, 30, 0, 0, zone);
    private final Clock clock = Clock.fixed(now.toInstant(), zone);

    @Test
    void faelligerScheduleFaehrtStoreAufZielposition() {
        InMemoryRepo repo = new InMemoryRepo();
        // 06:30, Zielposition 2 (= 98 % zu) für Store "store-1".
        repo.save(CoverSchedule.schedule(UUID.randomUUID(), "store-1", 2, LocalTime.of(6, 30), Set.of()));
        RecordingCovers covers = new RecordingCovers();

        new CoverScheduleService(repo, covers, clock).tick();

        assertEquals(List.of("store-1=2"), covers.positions);
    }

    @Test
    void nichtFaelligerScheduleTutNichts() {
        InMemoryRepo repo = new InMemoryRepo();
        repo.save(CoverSchedule.schedule(UUID.randomUUID(), "store-1", 50, LocalTime.of(7, 0), Set.of()));
        RecordingCovers covers = new RecordingCovers();

        new CoverScheduleService(repo, covers, clock).tick();

        assertTrue(covers.positions.isEmpty());
    }

    @Test
    void scheduleNurAmFalschenWochentagTutNichts() {
        InMemoryRepo repo = new InMemoryRepo();
        // now ist Montag -> nur Dienstag aktiv => nicht fällig.
        repo.save(CoverSchedule.schedule(
                UUID.randomUUID(), "store-1", 2, LocalTime.of(6, 30), Set.of(java.time.DayOfWeek.TUESDAY)));
        RecordingCovers covers = new RecordingCovers();

        new CoverScheduleService(repo, covers, clock).tick();

        assertTrue(covers.positions.isEmpty());
    }

    @Test
    void scheduleFeuertHoechstensEinmalProSlot() {
        InMemoryRepo repo = new InMemoryRepo();
        repo.save(CoverSchedule.schedule(UUID.randomUUID(), "store-1", 2, LocalTime.of(6, 30), Set.of()));
        RecordingCovers covers = new RecordingCovers();
        CoverScheduleService service = new CoverScheduleService(repo, covers, clock);

        service.tick();
        service.tick(); // gleicher Slot -> kein zweites Feuern

        assertEquals(List.of("store-1=2"), covers.positions);
    }

    @Test
    void countdownFeuertUndDeaktiviertSich() {
        InMemoryRepo repo = new InMemoryRepo();
        CoverSchedule cd = CoverSchedule.countdown(
                UUID.randomUUID(), "store-1", 80, now.toInstant().minusSeconds(1)); // bereits fällig
        repo.save(cd);
        RecordingCovers covers = new RecordingCovers();

        new CoverScheduleService(repo, covers, clock).tick();

        assertEquals(List.of("store-1=80"), covers.positions);
        assertFalse(repo.byId(cd.id()).orElseThrow().enabled(), "COUNTDOWN deaktiviert sich");
    }

    @Test
    void setEnabledUndDelete() {
        InMemoryRepo repo = new InMemoryRepo();
        CoverSchedule s = CoverSchedule.schedule(UUID.randomUUID(), "store-1", 2, LocalTime.of(8, 0), Set.of());
        repo.save(s);
        CoverScheduleService service = new CoverScheduleService(repo, new RecordingCovers(), clock);

        service.setEnabled(s.id(), false);
        assertFalse(repo.byId(s.id()).orElseThrow().enabled());

        service.delete(s.id());
        assertTrue(repo.byId(s.id()).isEmpty());
    }

    // --- Fakes ---

    private static final class RecordingCovers implements ControlCovers {
        final List<String> positions = new ArrayList<>();

        @Override public List<Cover> list() { return List.of(); }

        @Override public Cover command(String id, CoverCommand command) {
            return Cover.online(id, "Store", "", 100, Instant.EPOCH);
        }

        @Override public Cover setPosition(String id, int position) {
            positions.add(id + "=" + position);
            return Cover.online(id, "Store", "", position, Instant.EPOCH);
        }
    }

    private static final class InMemoryRepo implements CoverScheduleRepository {
        private final ConcurrentMap<UUID, CoverSchedule> store = new ConcurrentHashMap<>();

        @Override public CoverSchedule save(CoverSchedule s) { store.put(s.id(), s); return s; }
        @Override public Optional<CoverSchedule> byId(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<CoverSchedule> all() { return new ArrayList<>(store.values()); }
        @Override public List<CoverSchedule> allEnabled() {
            return store.values().stream().filter(CoverSchedule::enabled).toList();
        }
        @Override public void delete(UUID id) { store.remove(id); }
    }
}
