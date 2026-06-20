package fabianaschwanden.smarthome.domain.model.sensor;

import java.time.Instant;

/**
 * Momentaufnahme eines Umweltsensors (z. B. Tuya-Innensensor): Temperatur in °C und
 * relative Luftfeuchte in %. {@link #VALUE_UNKNOWN}, wenn der Wert nicht vorliegt.
 *
 * <p>Value Object: immutable {@code record}; nur lesend (kein Steuern).
 */
public record Sensor(
        String id,
        String name,
        String room,
        double temperature,
        int humidity,
        boolean online,
        Instant observedAt) {

    public static final double VALUE_UNKNOWN = -1000.0;
    public static final int HUMIDITY_UNKNOWN = -1;

    public Sensor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id darf nicht leer sein");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name darf nicht leer sein");
        }
        if (room == null) {
            room = "";
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt darf nicht null sein");
        }
    }

    public static Sensor online(String id, String name, String room, double temp, int humidity, Instant at) {
        return new Sensor(id, name, room, temp, humidity, true, at);
    }

    public static Sensor offline(String id, String name, String room, double lastTemp, int lastHumidity, Instant at) {
        return new Sensor(id, name, room, lastTemp, lastHumidity, false, at);
    }
}
