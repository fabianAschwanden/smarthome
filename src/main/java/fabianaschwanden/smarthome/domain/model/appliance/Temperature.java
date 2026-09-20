package fabianaschwanden.smarthome.domain.model.appliance;

import fabianaschwanden.smarthome.domain.model.thermal.ThermalActivity;

/**
 * Temperatur-Steuerung einer beheizten Anlage: Soll- und Ist-Temperatur (°C), der
 * erlaubte Sollbereich und was die Heizung gerade tut. {@code current = }{@link #UNKNOWN},
 * wenn die Ist-Temperatur nicht vorliegt. Value Object: immutable {@code record}.
 *
 * <p>{@code activity} kommt vom Gerät, nicht aus einem Vergleich der Temperaturen: Ob die
 * Heizung läuft, entscheidet die Steuerung des Spas mit ihrer eigenen Hysterese.
 */
public record Temperature(int target, int current, int min, int max, ThermalActivity activity) {

    public static final int UNKNOWN = -1;

    /** Ohne Angabe: die Heizung tut nichts. */
    public Temperature(int target, int current, int min, int max) {
        this(target, current, min, max, ThermalActivity.IDLE);
    }

    public Temperature {
        if (activity == null) {
            activity = ThermalActivity.IDLE;
        }
        if (min > max) {
            throw new IllegalArgumentException("min (" + min + ") darf nicht über max (" + max + ") liegen");
        }
        if (target < min || target > max) {
            throw new IllegalArgumentException(
                    "target muss " + min + ".." + max + " sein, war " + target);
        }
    }

    public Temperature withTarget(int newTarget) {
        return new Temperature(newTarget, current, min, max, activity);
    }

    public Temperature withCurrent(int newCurrent) {
        return new Temperature(target, newCurrent, min, max, activity);
    }

    public Temperature withActivity(ThermalActivity newActivity) {
        return new Temperature(target, current, min, max, newActivity);
    }

    /** Prüft eine Soll-Temperatur gegen den erlaubten Bereich. */
    public int requireInRange(int value) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("Temperatur muss " + min + ".." + max + " °C sein, war " + value);
        }
        return value;
    }
}
