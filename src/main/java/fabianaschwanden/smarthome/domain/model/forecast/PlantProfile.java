package fabianaschwanden.smarthome.domain.model.forecast;

import java.time.Instant;
import java.util.List;

/**
 * Das gelernte Verhalten der eigenen Anlage: je Stunden-Slot ein Faktor, der Strahlung in
 * Modulebene auf tatsächliche Leistung abbildet ({@code W} pro {@code W/m²}).
 *
 * <p>Warum je Stunde statt einem globalen Faktor: Verschattung ist tageszeitabhängig. Ein
 * Baum, der um 8 Uhr die halbe Fläche verdeckt, ist um 13 Uhr irrelevant – ein einzelner
 * Wirkungsgrad könnte das nie abbilden (SPEC §3.1).
 *
 * <p>{@code maxObservedPvWatt} ist die höchste je gemessene Leistung und dient als Deckel:
 * ein GTI-Ausreisser in der Prognose soll keine Leistung ergeben, die die Anlage physisch
 * nie erreicht hat.
 */
public record PlantProfile(
        List<Double> factorPerHour,
        double maxObservedPvWatt,
        Instant learnedAt,
        Confidence confidence) {

    /** Ein Faktor je Stunde des Tages. */
    public static final int SLOTS = 24;

    public PlantProfile {
        if (factorPerHour == null || factorPerHour.size() != SLOTS) {
            throw new IllegalArgumentException("factorPerHour braucht genau " + SLOTS + " Werte");
        }
        if (factorPerHour.stream().anyMatch(f -> f == null || f < 0 || !Double.isFinite(f))) {
            throw new IllegalArgumentException("factorPerHour darf nur endliche, nicht-negative Werte enthalten");
        }
        if (maxObservedPvWatt < 0) {
            throw new IllegalArgumentException("maxObservedPvWatt darf nicht negativ sein: " + maxObservedPvWatt);
        }
        if (learnedAt == null) {
            throw new IllegalArgumentException("learnedAt darf nicht null sein");
        }
        if (confidence == null) {
            throw new IllegalArgumentException("confidence darf nicht null sein");
        }
        factorPerHour = List.copyOf(factorPerHour);
    }

    /** Faktor für einen Stunden-Slot (0–23). */
    public double factorAt(int hourOfDay) {
        if (hourOfDay < 0 || hourOfDay > 23) {
            throw new IllegalArgumentException("hourOfDay muss zwischen 0 und 23 liegen: " + hourOfDay);
        }
        return factorPerHour.get(hourOfDay);
    }
}
