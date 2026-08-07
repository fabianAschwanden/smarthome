package fabianaschwanden.smarthome.adapter.in.rest.dto.applianceschedule;

import fabianaschwanden.smarthome.domain.model.applianceschedule.ApplianceSchedule;

/** Transport-Objekt eines Wellness-Schaltauftrags. */
public record ApplianceScheduleDto(
        String id, String applianceId, int targetTemp, String fireAt, boolean enabled) {

    public static ApplianceScheduleDto from(ApplianceSchedule schedule) {
        return new ApplianceScheduleDto(
                schedule.id().toString(),
                schedule.applianceId(),
                schedule.targetTemp(),
                schedule.fireAt().toString(),
                schedule.enabled());
    }
}
