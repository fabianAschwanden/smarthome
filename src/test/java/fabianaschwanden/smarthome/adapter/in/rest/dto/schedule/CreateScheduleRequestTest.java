package fabianaschwanden.smarthome.adapter.in.rest.dto.schedule;

import fabianaschwanden.smarthome.domain.model.schedule.ScheduleType;
import fabianaschwanden.smarthome.domain.model.schedule.SwitchSchedule;
import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deckt die {@code toDomain}-Zweige und Default-/Fehlerpfade von CreateScheduleRequest ab. */
@QuarkusTest
class CreateScheduleRequestTest {

    private static final UUID ID = UUID.randomUUID();

    @Test
    void randomBautAggregatMitFenster() {
        CreateScheduleRequest req = new CreateScheduleRequest(
                "lampe", ScheduleType.RANDOM, SwitchState.ON, null,
                null, null, null, "20:00", "23:00", null);

        SwitchSchedule schedule = req.toDomain(ID);

        assertEquals(ScheduleType.RANDOM, schedule.type());
        assertTrue(schedule.enabled(), "Default enabled");
    }

    @Test
    void inchingBautAggregatMitPulse() {
        CreateScheduleRequest req = new CreateScheduleRequest(
                "lampe", ScheduleType.INCHING, null, null,
                null, null, null, null, null, 30);

        SwitchSchedule schedule = req.toDomain(ID);

        assertEquals(ScheduleType.INCHING, schedule.type());
    }

    @Test
    void defaultActionIstOnUndDisabledWirdUebernommen() {
        CreateScheduleRequest req = new CreateScheduleRequest(
                "lampe", ScheduleType.SCHEDULE, null, false,
                "07:30", List.of(), null, null, null, null);

        SwitchSchedule schedule = req.toDomain(ID);

        assertEquals(SwitchState.ON, schedule.action(), "fehlende action -> ON");
        assertFalse(schedule.enabled(), "enabled=false wird übernommen");
    }

    @Test
    void fehlendeUhrzeitWirftFehler() {
        CreateScheduleRequest req = new CreateScheduleRequest(
                "lampe", ScheduleType.SCHEDULE, SwitchState.ON, null,
                null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> req.toDomain(ID));
    }

    @Test
    void fehlendeCountdownSekundenWerfenFehler() {
        CreateScheduleRequest req = new CreateScheduleRequest(
                "lampe", ScheduleType.COUNTDOWN, SwitchState.OFF, null,
                null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> req.toDomain(ID));
    }
}
