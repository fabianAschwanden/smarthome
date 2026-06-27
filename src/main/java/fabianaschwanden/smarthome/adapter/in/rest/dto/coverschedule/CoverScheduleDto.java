package fabianaschwanden.smarthome.adapter.in.rest.dto.coverschedule;

import fabianaschwanden.smarthome.domain.model.coverschedule.CoverSchedule;

import java.util.List;

/**
 * Transport-Objekt einer Storen-Zeitsteuerungs-Regel. Felder je Typ befüllt
 * (null/leer, wenn nicht relevant). Zeit als "HH:mm", Wochentage als Namen.
 * {@code position}: 0 = zu, 100 = offen (Geräte-Skala).
 */
public record CoverScheduleDto(
        String id,
        String coverId,
        String type,
        int position,
        boolean enabled,
        String time,
        List<String> weekdays,
        String fireAt) {

    public static CoverScheduleDto from(CoverSchedule s) {
        return new CoverScheduleDto(
                s.id().toString(),
                s.coverId(),
                s.type().name(),
                s.position(),
                s.enabled(),
                s.time() == null ? null : s.time().toString(),
                s.weekdays().stream().map(Enum::name).toList(),
                s.fireAt() == null ? null : s.fireAt().toString());
    }
}
