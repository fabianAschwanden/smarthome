package fabianaschwanden.smarthome.domain.model.appliance;

/**
 * Temperatur-Steuerung einer beheizten Anlage: Soll- und Ist-Temperatur (°C) sowie
 * der erlaubte Sollbereich. {@code current = }{@link #UNKNOWN}, wenn die Ist-Temperatur
 * nicht vorliegt. Value Object: immutable {@code record}.
 */
public record Temperature(int target, int current, int min, int max) {

    public static final int UNKNOWN = -1;

    public Temperature {
        if (min > max) {
            throw new IllegalArgumentException("min (" + min + ") darf nicht über max (" + max + ") liegen");
        }
        if (target < min || target > max) {
            throw new IllegalArgumentException(
                    "target muss " + min + ".." + max + " sein, war " + target);
        }
    }

    public Temperature withTarget(int newTarget) {
        return new Temperature(newTarget, current, min, max);
    }

    public Temperature withCurrent(int newCurrent) {
        return new Temperature(target, newCurrent, min, max);
    }

    /** Prüft eine Soll-Temperatur gegen den erlaubten Bereich. */
    public int requireInRange(int value) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("Temperatur muss " + min + ".." + max + " °C sein, war " + value);
        }
        return value;
    }
}
