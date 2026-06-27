package fabianaschwanden.smarthome.adapter.in.rest.dto.coverschedule;

import fabianaschwanden.smarthome.domain.model.coverschedule.CoverSchedule;
import fabianaschwanden.smarthome.domain.model.coverschedule.CoverScheduleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * Anlage-Anforderung einer Storen-Zeitsteuerung. Je {@code type} sind
 * unterschiedliche Felder nötig; {@link #toDomain(UUID)} baut das validierte Aggregat.
 *
 * @param coverId          Ziel-Store.
 * @param position         Zielposition 0 = zu, 100 = offen (Geräte-Skala).
 * @param countdownSeconds COUNTDOWN: Verzögerung ab jetzt in Sekunden.
 */
public record CreateCoverScheduleRequest(
        @NotBlank String coverId,
        @NotNull CoverScheduleType type,
        @Min(0) @Max(100) int position,
        Boolean enabled,
        String time,
        List<DayOfWeek> weekdays,
        Long countdownSeconds) {

    public CoverSchedule toDomain(UUID id) {
        boolean effectiveEnabled = enabled == null || enabled;
        CoverSchedule schedule = switch (type) {
            case SCHEDULE -> CoverSchedule.schedule(id, coverId, position, parseTime(time), parseWeekdays());
            case COUNTDOWN -> CoverSchedule.countdown(id, coverId, position,
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
