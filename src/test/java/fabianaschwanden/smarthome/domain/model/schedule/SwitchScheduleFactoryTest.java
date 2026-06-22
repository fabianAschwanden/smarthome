package fabianaschwanden.smarthome.domain.model.schedule;

import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class SwitchScheduleFactoryTest {

    private final UUID id = UUID.randomUUID();

    @Test
    void randomFabrikSetztFenster() {
        SwitchSchedule s = SwitchSchedule.random(
                id, "lampe", SwitchState.ON, LocalTime.of(18, 0), LocalTime.of(22, 0));
        assertEquals(ScheduleType.RANDOM, s.type());
        assertEquals(LocalTime.of(18, 0), s.windowStart());
        assertEquals(LocalTime.of(22, 0), s.windowEnd());
    }

    @Test
    void randomBrauchtFenster() {
        assertThrows(IllegalArgumentException.class, () -> new SwitchSchedule(
                id, "lampe", ScheduleType.RANDOM, SwitchState.ON, true,
                null, Set.of(), null, null, null, null));
    }

    @Test
    void inchingFabrikSetztDauer() {
        SwitchSchedule s = SwitchSchedule.inching(id, "lampe", 30);
        assertEquals(ScheduleType.INCHING, s.type());
        assertEquals(30, s.pulseSeconds());
        assertEquals(SwitchState.ON, s.action());
    }

    @Test
    void countdownFabrikSetztZeitpunkt() {
        Instant at = Instant.parse("2026-06-22T20:00:00Z");
        SwitchSchedule s = SwitchSchedule.countdown(id, "lampe", SwitchState.OFF, at);
        assertEquals(ScheduleType.COUNTDOWN, s.type());
        assertEquals(at, s.fireAt());
    }

    @Test
    void idDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class, () -> new SwitchSchedule(
                null, "lampe", ScheduleType.INCHING, SwitchState.ON, true,
                null, Set.of(), null, null, null, 5));
    }

    @Test
    void switchIdDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class, () -> new SwitchSchedule(
                id, " ", ScheduleType.INCHING, SwitchState.ON, true,
                null, Set.of(), null, null, null, 5));
    }

    @Test
    void typeDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class, () -> new SwitchSchedule(
                id, "lampe", null, SwitchState.ON, true,
                null, Set.of(), null, null, null, 5));
    }

    @Test
    void actionDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class, () -> new SwitchSchedule(
                id, "lampe", ScheduleType.INCHING, null, true,
                null, Set.of(), null, null, null, 5));
    }

    @Test
    void weekdaysSindUnveraenderbar() {
        SwitchSchedule s = SwitchSchedule.schedule(
                id, "lampe", SwitchState.ON, LocalTime.of(7, 0), Set.of());
        assertThrows(UnsupportedOperationException.class,
                () -> s.weekdays().add(java.time.DayOfWeek.MONDAY));
    }
}
