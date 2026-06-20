package fabianaschwanden.smarthome.adapter.in.rest.dto.schedule;

import fabianaschwanden.smarthome.domain.model.schedule.SwitchSchedule;

import java.util.List;

/**
 * Transport-Objekt einer Zeitsteuerungs-Regel. Felder sind je Typ befüllt
 * (null/leer, wenn für den Typ nicht relevant). Zeiten als ISO-Strings
 * ("HH:mm"), Wochentage als Namen (z. B. "MONDAY").
 */
public record ScheduleDto(
        String id,
        String switchId,
        String type,
        String action,
        boolean enabled,
        String time,
        List<String> weekdays,
        String fireAt,
        String windowStart,
        String windowEnd,
        Integer pulseSeconds) {

    public static ScheduleDto from(SwitchSchedule s) {
        return new ScheduleDto(
                s.id().toString(),
                s.switchId(),
                s.type().name(),
                s.action().name(),
                s.enabled(),
                s.time() == null ? null : s.time().toString(),
                s.weekdays().stream().map(Enum::name).toList(),
                s.fireAt() == null ? null : s.fireAt().toString(),
                s.windowStart() == null ? null : s.windowStart().toString(),
                s.windowEnd() == null ? null : s.windowEnd().toString(),
                s.pulseSeconds());
    }
}
