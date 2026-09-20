package fabianaschwanden.smarthome.domain.model.climate;

import fabianaschwanden.smarthome.domain.model.thermal.ThermalActivity;

import java.time.Instant;

/**
 * Momentaufnahme einer Klimaanlage: Ein/Aus, Boost (Turbo), Modus, Soll-, Ist- und
 * Außentemperatur. {@code currentTemp} bzw. {@code outdoorTemp} = {@link #TEMP_UNKNOWN},
 * wenn das Gerät die jeweilige Temperatur nicht meldet. {@code boost} = Turbo-Modus für
 * maximale Leistung. Soll-Temperatur muss im erlaubten Bereich liegen (Invariante).
 *
 * <p>{@code active} unterscheidet «nicht erreichbar» von «bewusst stillgelegt»: Eine
 * Anlage, die über den Winter vom Strom ist, ist nicht kaputt – sie wird nur nicht
 * mehr angesprochen. Eine stillgelegte Anlage ist nie {@code online}.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record Climate(
        String id,
        String name,
        String room,
        boolean power,
        boolean boost,
        ClimateMode mode,
        int targetTemp,
        int currentTemp,
        int outdoorTemp,
        boolean online,
        boolean active,
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
        if (!active && online) {
            throw new IllegalArgumentException("eine stillgelegte Anlage kann nicht online sein");
        }
    }

    /**
     * Was die Anlage gerade tut. Die Midea-Steuerung meldet keinen Heiz-/Kühlindikator,
     * also wird aus Betrieb, Modus und Temperaturen abgeleitet: Aus oder nur Lüften heisst
     * nichts; Kühlen und Heizen sind eindeutig; im Automatikmodus entscheidet die Richtung
     * von Ist nach Soll - und ohne Ist-Wert wird nichts behauptet.
     */
    public ThermalActivity activity() {
        if (!power) {
            return ThermalActivity.IDLE;
        }
        return switch (mode) {
            case COOL -> ThermalActivity.COOLING;
            case HEAT -> ThermalActivity.HEATING;
            case AUTO -> {
                if (currentTemp == TEMP_UNKNOWN || currentTemp == targetTemp) {
                    yield ThermalActivity.IDLE;
                }
                yield currentTemp < targetTemp ? ThermalActivity.HEATING : ThermalActivity.COOLING;
            }
            case FAN -> ThermalActivity.IDLE;
        };
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
