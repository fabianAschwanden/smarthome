package fabianaschwanden.smarthome.adapter.in.rest.dto.schedule;

import fabianaschwanden.smarthome.domain.model.schedule.ScheduleType;
import fabianaschwanden.smarthome.domain.model.schedule.SwitchSchedule;
import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Anlage-/Änderungs-Anforderung einer Zeitsteuerung. Je {@code type} sind
 * unterschiedliche Felder nötig; {@link #toDomain(UUID)} baut das validierte
 * Aggregat (weitere Invarianten erzwingt der Domänen-Record).
 *
 * @param countdownSeconds COUNTDOWN: Verzögerung ab jetzt in Sekunden.
 */
public record CreateScheduleRequest(
        @NotBlank String switchId,
        @NotNull ScheduleType type,
        SwitchState action,
        Boolean enabled,
        String time,
        List<DayOfWeek> weekdays,
        Long countdownSeconds,
        String windowStart,
        String windowEnd,
        Integer pulseSeconds) {

    public SwitchSchedule toDomain(UUID id) {
        SwitchState effectiveAction = action == null ? SwitchState.ON : action;
        boolean effectiveEnabled = enabled == null || enabled;
        SwitchSchedule schedule = switch (type) {
            case SCHEDULE -> SwitchSchedule.schedule(id, switchId, effectiveAction,
                    parseTime(time, "time"), parseWeekdays());
            case COUNTDOWN -> SwitchSchedule.countdown(id, switchId, effectiveAction,
                    Instant.now().plus(Duration.ofSeconds(required(countdownSeconds, "countdownSeconds"))));
            case RANDOM -> SwitchSchedule.random(id, switchId, effectiveAction,
                    parseTime(windowStart, "windowStart"), parseTime(windowEnd, "windowEnd"));
            case INCHING -> SwitchSchedule.inching(id, switchId, required(pulseSeconds, "pulseSeconds"));
        };
        return effectiveEnabled ? schedule : schedule.withEnabled(false);
    }

    private Set<DayOfWeek> parseWeekdays() {
        return weekdays == null ? Set.of() : weekdays.stream().collect(Collectors.toUnmodifiableSet());
    }

    private static LocalTime parseTime(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " ist erforderlich (Format HH:mm)");
        }
        return LocalTime.parse(value);
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " ist erforderlich");
        }
        return value;
    }
}
