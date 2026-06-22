package fabianaschwanden.smarthome.domain.model.schedule;

import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SwitchScheduleTest {

    private final UUID id = UUID.randomUUID();

    @Test
    void scheduleBrauchtUhrzeit() {
        assertThrows(IllegalArgumentException.class, () -> new SwitchSchedule(
                id, "lampe", ScheduleType.SCHEDULE, SwitchState.ON, true,
                null, Set.of(), null, null, null, null));
    }

    @Test
    void countdownBrauchtZeitpunkt() {
        assertThrows(IllegalArgumentException.class, () -> new SwitchSchedule(
                id, "lampe", ScheduleType.COUNTDOWN, SwitchState.ON, true,
                null, Set.of(), null, null, null, null));
    }

    @Test
    void inchingBrauchtPositiveDauer() {
        assertThrows(IllegalArgumentException.class, () -> SwitchSchedule.inching(id, "lampe", 0));
    }

    @Test
    void leereWochentageBedeutenTaeglich() {
        SwitchSchedule s = SwitchSchedule.schedule(id, "lampe", SwitchState.ON, LocalTime.of(7, 0), Set.of());
        assertTrue(s.appliesOn(DayOfWeek.MONDAY));
        assertTrue(s.appliesOn(DayOfWeek.SUNDAY));
    }

    @Test
    void nurAusgewaehlteWochentage() {
        SwitchSchedule s = SwitchSchedule.schedule(
                id, "lampe", SwitchState.ON, LocalTime.of(7, 0), Set.of(DayOfWeek.MONDAY));
        assertTrue(s.appliesOn(DayOfWeek.MONDAY));
        assertFalse(s.appliesOn(DayOfWeek.TUESDAY));
    }

    @Test
    void withEnabledLiefertNeueInstanz() {
        SwitchSchedule s = SwitchSchedule.countdown(id, "lampe", SwitchState.OFF, Instant.now());
        assertEquals(false, s.withEnabled(false).enabled());
        assertTrue(s.enabled()); // Original unverändert
    }
}
