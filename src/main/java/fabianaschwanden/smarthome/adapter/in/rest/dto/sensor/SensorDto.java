package fabianaschwanden.smarthome.adapter.in.rest.dto.sensor;

import fabianaschwanden.smarthome.domain.model.sensor.Sensor;

/** Transport-Objekt eines Umweltsensors. temperature in °C, humidity in %. */
public record SensorDto(
        String id,
        String name,
        String room,
        double temperature,
        int humidity,
        boolean online,
        String observedAt) {

    public static SensorDto from(Sensor s) {
        return new SensorDto(s.id(), s.name(), s.room(), s.temperature(), s.humidity(),
                s.online(), s.observedAt().toString());
    }
}
