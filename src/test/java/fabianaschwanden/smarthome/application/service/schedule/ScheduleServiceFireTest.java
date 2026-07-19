package fabianaschwanden.smarthome.application.service.schedule;

import fabianaschwanden.smarthome.domain.model.schedule.SwitchSchedule;
import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import fabianaschwanden.smarthome.domain.model.tuya.TuyaSwitch;
import fabianaschwanden.smarthome.domain.port.in.tuya.ControlSwitches;
import fabianaschwanden.smarthome.domain.port.out.schedule.ScheduleRepository;
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
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deckt die Ausführungspfade des Schedulers ab (tick/evaluate/fire) inkl.
 * Selbstdeaktivierung bei COUNTDOWN/INCHING und der RANDOM-Auswahl mit festem Seed.
 * Ergänzt {@link ScheduleSchedulerTest} (reine isDue-Logik).
 */
@QuarkusTest
class ScheduleServiceFireTest {

    private final ZoneId zone = ZoneId.of("Europe/Zurich");
    // Montag, 07:00 lokale Zeit.
    private final ZonedDateTime now = ZonedDateTime.of(2026, 6, 15, 7, 0, 0, 0, zone);
    private final Clock clock = Clock.fixed(now.toInstant(), zone);

    private ScheduleService runner(ScheduleRepository repo, RecordingSwitches switches) {
        return new ScheduleService(repo, switches, clock, new Random(1));
    }

    @Test
    void tickFeuertFaelligenScheduleUndSchaltetDenSchalter() {
        SwitchSchedule s = SwitchSchedule.schedule(
                UUID.randomUUID(), "lampe", SwitchState.ON, LocalTime.of(7, 0), java.util.Set.of());
        InMemoryRepo repo = new InMemoryRepo();
        repo.save(s);
        RecordingSwitches switches = new RecordingSwitches();

        runner(repo, switches).tick();

        assertEquals(1, switches.calls.size());
        assertEquals("lampe", switches.calls.get(0).id());
        assertEquals(SwitchState.ON, switches.calls.get(0).state());
        assertTrue(switches.calls.get(0).confirmed());
    }

    @Test
    void tickIgnoriertNichtFaelligenSchedule() {
        SwitchSchedule s = SwitchSchedule.schedule(
                UUID.randomUUID(), "lampe", SwitchState.ON, LocalTime.of(8, 0), java.util.Set.of());
        InMemoryRepo repo = new InMemoryRepo();
        repo.save(s);
        RecordingSwitches switches = new RecordingSwitches();

        runner(repo, switches).tick();

        assertTrue(switches.calls.isEmpty());
    }

    @Test
    void countdownFeuertUndDeaktiviertSichSelbst() {
        SwitchSchedule s = SwitchSchedule.countdown(
                UUID.randomUUID(), "lampe", SwitchState.OFF, now.toInstant().minusSeconds(10));
        InMemoryRepo repo = new InMemoryRepo();
        repo.save(s);
        RecordingSwitches switches = new RecordingSwitches();

        runner(repo, switches).tick();

        assertEquals(1, switches.calls.size());
        assertEquals(SwitchState.OFF, switches.calls.get(0).state());
        assertFalse(repo.byId(s.id()).orElseThrow().enabled(), "COUNTDOWN deaktiviert sich");
    }

    @Test
    void inchingSchaltetEinUndDeaktiviertSichSelbst() throws InterruptedException {
        // pulseSeconds=1 -> Auto-Off-Thread schaltet nach 1s OFF.
        SwitchSchedule s = SwitchSchedule.inching(UUID.randomUUID(), "lampe", 1);
        InMemoryRepo repo = new InMemoryRepo();
        repo.save(s);
        RecordingSwitches switches = new RecordingSwitches();

        runner(repo, switches).tick();

        // Sofort: EIN geschaltet, Regel deaktiviert.
        assertEquals(SwitchState.ON, switches.calls.get(0).state());
        assertFalse(repo.byId(s.id()).orElseThrow().enabled(), "INCHING deaktiviert sich");

        // Auto-Off folgt asynchron nach pulseSeconds.
        long deadline = System.currentTimeMillis() + 5000;
        while (switches.calls.size() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(2, switches.calls.size(), "Auto-Off erwartet");
        assertEquals(SwitchState.OFF, switches.calls.get(1).state());
    }

    @Test
    void randomWaehltZeitImFensterUndFeuertNurEinmalProTag() {
        // Festes Seed (Random(1)) -> deterministische Auswahl im Fenster. Da die Auswahl
        // an die ScheduleService-Instanz gebunden ist (interne Merker), wird der Pick auf
        // einem Wegwerf-Runner ermittelt und auf einem frischen, gleich geseedeten Runner
        // (gleiche UUID -> gleicher Pick) verifiziert.
        UUID id = UUID.randomUUID();
        SwitchSchedule s = SwitchSchedule.random(
                id, "lampe", SwitchState.ON, LocalTime.of(0, 0), LocalTime.of(23, 59));

        LocalTime pick = ermittlePick(runner(new InMemoryRepo(), new RecordingSwitches()), s);
        ZonedDateTime atPick = ZonedDateTime.of(2026, 6, 15,
                pick.getHour(), pick.getMinute(), 0, 0, zone);

        ScheduleService fresh = runner(new InMemoryRepo(), new RecordingSwitches());
        assertEquals(Optional.of(SwitchState.ON), fresh.isDue(s, atPick));
        assertTrue(fresh.isDue(s, atPick).isEmpty(), "RANDOM feuert pro Tag-Slot nur einmal");
    }

    /** Ruft isDue über alle Minuten des Tages, bis der eine RANDOM-Slot gefunden ist. */
    private LocalTime ermittlePick(ScheduleService runner, SwitchSchedule s) {
        for (int minute = 0; minute < 24 * 60; minute++) {
            ZonedDateTime t = ZonedDateTime.of(2026, 6, 15, minute / 60, minute % 60, 0, 0, zone);
            if (runner.isDue(s, t).isPresent()) {
                return LocalTime.of(minute / 60, minute % 60);
            }
        }
        throw new IllegalStateException("RANDOM hat im ganzen Tag nicht gefeuert");
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
        @Override public List<SwitchSchedule> all() { return List.copyOf(store.values()); }
        @Override public List<SwitchSchedule> allEnabled() {
            return store.values().stream().filter(SwitchSchedule::enabled).toList();
        }
        @Override public void delete(UUID id) { store.remove(id); }
    }

    private record Call(String id, SwitchState state, boolean confirmed) { }

    private static final class RecordingSwitches implements ControlSwitches {
        private final List<Call> calls = new ArrayList<>();

        @Override public List<TuyaSwitch> list() { return List.of(); }
        @Override public synchronized TuyaSwitch switchTo(String id, SwitchState state, boolean confirmed) {
            calls.add(new Call(id, state, confirmed));
            return TuyaSwitch.online(id, id, "", state, false, "", Instant.now());
        }
    }
}
