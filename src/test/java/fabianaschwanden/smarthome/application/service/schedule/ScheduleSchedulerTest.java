package fabianaschwanden.smarthome.application.service.schedule;

import fabianaschwanden.smarthome.domain.model.schedule.SwitchSchedule;
import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import fabianaschwanden.smarthome.domain.port.in.tuya.ControlSwitches;
import fabianaschwanden.smarthome.domain.model.tuya.TuyaSwitch;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ScheduleSchedulerTest {

    private final ZoneId zone = ZoneId.of("Europe/Zurich");
    // Montag, 07:00 lokale Zeit.
    private final ZonedDateTime now = ZonedDateTime.of(2026, 6, 15, 7, 0, 0, 0, zone);
    private final Clock clock = Clock.fixed(now.toInstant(), zone);

    private ScheduleService runner(ScheduleRepository repo) {
        return new ScheduleService(repo, new NoopSwitches(), clock, new Random(1));
    }

    @Test
    void scheduleFeuertZurPassendenMinute() {
        ScheduleService runner = runner(new StubRepo());
        SwitchSchedule s = SwitchSchedule.schedule(
                UUID.randomUUID(), "lampe", SwitchState.ON, LocalTime.of(7, 0), java.util.Set.of());
        assertEquals(Optional.of(SwitchState.ON), runner.isDue(s, now));
    }

    @Test
    void scheduleFeuertNichtZurFalschenMinute() {
        ScheduleService runner = runner(new StubRepo());
        SwitchSchedule s = SwitchSchedule.schedule(
                UUID.randomUUID(), "lampe", SwitchState.ON, LocalTime.of(8, 0), java.util.Set.of());
        assertTrue(runner.isDue(s, now).isEmpty());
    }

    @Test
    void scheduleFeuertNichtAmFalschenWochentag() {
        ScheduleService runner = runner(new StubRepo());
        SwitchSchedule s = SwitchSchedule.schedule(UUID.randomUUID(), "lampe", SwitchState.ON,
                LocalTime.of(7, 0), java.util.Set.of(java.time.DayOfWeek.SUNDAY));
        assertTrue(runner.isDue(s, now).isEmpty()); // now ist Montag
    }

    @Test
    void scheduleFeuertProMinuteNurEinmal() {
        ScheduleService runner = runner(new StubRepo());
        SwitchSchedule s = SwitchSchedule.schedule(
                UUID.randomUUID(), "lampe", SwitchState.ON, LocalTime.of(7, 0), java.util.Set.of());
        assertTrue(runner.isDue(s, now).isPresent());
        assertTrue(runner.isDue(s, now).isEmpty()); // zweiter Tick in derselben Minute -> nichts
    }

    @Test
    void countdownFeuertNachZeitpunkt() {
        ScheduleService runner = runner(new StubRepo());
        SwitchSchedule past = SwitchSchedule.countdown(
                UUID.randomUUID(), "lampe", SwitchState.OFF, now.toInstant().minusSeconds(10));
        SwitchSchedule future = SwitchSchedule.countdown(
                UUID.randomUUID(), "lampe", SwitchState.OFF, now.toInstant().plusSeconds(60));
        assertEquals(Optional.of(SwitchState.OFF), runner.isDue(past, now));
        assertTrue(runner.isDue(future, now).isEmpty());
    }

    @Test
    void inchingIstImmerFaellig() {
        ScheduleService runner = runner(new StubRepo());
        SwitchSchedule s = SwitchSchedule.inching(UUID.randomUUID(), "lampe", 30);
        assertEquals(Optional.of(SwitchState.ON), runner.isDue(s, now));
    }

    private static final class StubRepo implements ScheduleRepository {
        @Override public SwitchSchedule save(SwitchSchedule s) { return s; }
        @Override public Optional<SwitchSchedule> byId(UUID id) { return Optional.empty(); }
        @Override public List<SwitchSchedule> forSwitch(String switchId) { return List.of(); }
        @Override public List<SwitchSchedule> allEnabled() { return List.of(); }
        @Override public void delete(UUID id) { }
    }

    private static final class NoopSwitches implements ControlSwitches {
        @Override public List<TuyaSwitch> list() { return List.of(); }
        @Override public TuyaSwitch switchTo(String id, SwitchState state, boolean confirmed) {
            return TuyaSwitch.online(id, id, "", state, false, Instant.now());
        }
    }
}
