package fabianaschwanden.smarthome.domain.model.climate;

import java.time.Instant;

/**
 * Momentaufnahme einer Klimaanlage: Ein/Aus, Modus, Soll-, Ist- und Außentemperatur.
 * {@code currentTemp} bzw. {@code outdoorTemp} = {@link #TEMP_UNKNOWN}, wenn das Gerät
 * die jeweilige Temperatur nicht meldet. Soll-Temperatur muss im erlaubten Bereich
 * liegen (Invariante).
 *
 * <p>Value Object: immutable {@code record}.
 */
public record Climate(
        String id,
        String name,
        String room,
        boolean power,
        ClimateMode mode,
        int targetTemp,
        int currentTemp,
        int outdoorTemp,
        boolean online,
        Instant observedAt) {

    public static final int TEMP_UNKNOWN = -1;
    public static final int MIN_TEMP = 16;
    public static final int MAX_TEMP = 30;

    public Climate {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id darf nicht leer sein");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name darf nicht leer sein");
        }
        if (room == null) {
            room = "";
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode darf nicht null sein");
        }
        if (targetTemp < MIN_TEMP || targetTemp > MAX_TEMP) {
            throw new IllegalArgumentException(
                    "targetTemp muss " + MIN_TEMP + ".." + MAX_TEMP + " sein, war " + targetTemp);
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt darf nicht null sein");
        }
    }

    /** Validiert eine Soll-Temperatur gegen den erlaubten Bereich. */
    public static int requireValidTarget(int temperature) {
        if (temperature < MIN_TEMP || temperature > MAX_TEMP) {
            throw new IllegalArgumentException(
                    "Temperatur muss " + MIN_TEMP + ".." + MAX_TEMP + " °C sein, war " + temperature);
        }
        return temperature;
    }
}
