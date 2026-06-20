package fabianaschwanden.smarthome.adapter.in.rest.dto.safety;

import fabianaschwanden.smarthome.domain.model.safety.SmokeDetector;

/** Transport-Objekt eines Rauchmelders. alarm: "OK"/"ALARM", battery in % (-1 unbekannt). */
public record SmokeDetectorDto(
        String id,
        String name,
        String room,
        String alarm,
        int battery,
        boolean online,
        String observedAt) {

    public static SmokeDetectorDto from(SmokeDetector s) {
        return new SmokeDetectorDto(s.id(), s.name(), s.room(), s.alarm().name(), s.battery(),
                s.online(), s.observedAt().toString());
    }
}
