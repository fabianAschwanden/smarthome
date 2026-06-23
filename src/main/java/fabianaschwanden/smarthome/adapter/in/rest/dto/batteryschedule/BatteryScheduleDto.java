package fabianaschwanden.smarthome.adapter.in.rest.dto.batteryschedule;

import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;

import java.util.List;

/**
 * Transport-Objekt einer Batterie-Zeitsteuerungs-Regel. Felder je Typ befüllt
 * (null/leer, wenn nicht relevant). Zeit als "HH:mm", Wochentage als Namen.
 */
public record BatteryScheduleDto(
        String id,
        String type,
        String action,
        boolean enabled,
        String time,
        List<String> weekdays,
        String fireAt) {

    public static BatteryScheduleDto from(BatterySchedule s) {
        return new BatteryScheduleDto(
                s.id().toString(),
                s.type().name(),
                s.action().name(),
                s.enabled(),
                s.time() == null ? null : s.time().toString(),
                s.weekdays().stream().map(Enum::name).toList(),
                s.fireAt() == null ? null : s.fireAt().toString());
    }
}
