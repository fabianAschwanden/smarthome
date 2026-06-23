package fabianaschwanden.smarthome.adapter.in.rest.dto.batteryschedule;

import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatteryScheduleType;
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
 * Anlage-Anforderung einer Batterie-Zeitsteuerung. Je {@code type} sind
 * unterschiedliche Felder nötig; {@link #toDomain(UUID)} baut das validierte Aggregat.
 *
 * @param action           Relais-Aktion (ON/OFF); Default ON.
 * @param countdownSeconds COUNTDOWN: Verzögerung ab jetzt in Sekunden.
 */
public record CreateBatteryScheduleRequest(
        @NotNull BatteryScheduleType type,
        RelayState action,
        Boolean enabled,
        String time,
        List<DayOfWeek> weekdays,
        Long countdownSeconds) {

    public BatterySchedule toDomain(UUID id) {
        RelayState effectiveAction = action == null ? RelayState.ON : action;
        boolean effectiveEnabled = enabled == null || enabled;
        BatterySchedule schedule = switch (type) {
            case SCHEDULE -> BatterySchedule.schedule(id, effectiveAction,
                    parseTime(time), parseWeekdays());
            case COUNTDOWN -> BatterySchedule.countdown(id, effectiveAction,
                    Instant.now().plus(Duration.ofSeconds(required(countdownSeconds, "countdownSeconds"))));
        };
        return effectiveEnabled ? schedule : schedule.withEnabled(false);
    }

    private Set<DayOfWeek> parseWeekdays() {
        return weekdays == null ? Set.of() : weekdays.stream().collect(Collectors.toUnmodifiableSet());
    }

    private static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("time ist erforderlich (Format HH:mm)");
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
