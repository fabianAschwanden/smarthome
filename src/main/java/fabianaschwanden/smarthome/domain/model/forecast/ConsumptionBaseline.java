package fabianaschwanden.smarthome.domain.model.forecast;

import java.time.DayOfWeek;
import java.util.List;

/**
 * Der typische Hausverbrauch je Stunden-Slot – getrennt nach Werktag und Wochenende, weil
 * sich die Tage im Verlauf deutlich unterscheiden (SPEC §3.3). Werte in Watt.
 */
public record ConsumptionBaseline(List<Double> weekdayWatt, List<Double> weekendWatt) {

    /** Ein Wert je Stunde des Tages. */
    public static final int SLOTS = 24;

    public ConsumptionBaseline {
        weekdayWatt = validated(weekdayWatt, "weekdayWatt");
        weekendWatt = validated(weekendWatt, "weekendWatt");
    }

    private static List<Double> validated(List<Double> values, String name) {
        if (values == null || values.size() != SLOTS) {
            throw new IllegalArgumentException(name + " braucht genau " + SLOTS + " Werte");
        }
        if (values.stream().anyMatch(v -> v == null || v < 0 || !Double.isFinite(v))) {
            throw new IllegalArgumentException(name + " darf nur endliche, nicht-negative Werte enthalten");
        }
        return List.copyOf(values);
    }

    /** Erwarteter Verbrauch für Wochentag und Stunden-Slot. */
    public double wattAt(DayOfWeek day, int hourOfDay) {
        if (day == null) {
            throw new IllegalArgumentException("day darf nicht null sein");
        }
        if (hourOfDay < 0 || hourOfDay > 23) {
            throw new IllegalArgumentException("hourOfDay muss zwischen 0 und 23 liegen: " + hourOfDay);
        }
        return isWeekend(day) ? weekendWatt.get(hourOfDay) : weekdayWatt.get(hourOfDay);
    }

    /** Samstag und Sonntag gelten als Wochenende. */
    public static boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
