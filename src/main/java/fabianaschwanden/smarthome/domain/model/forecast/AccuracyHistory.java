package fabianaschwanden.smarthome.domain.model.forecast;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Die letzten Tage im Vergleich Prognose gegen Ist, samt mittlerem relativem Fehler
 * (MAPE – mean absolute percentage error).
 *
 * <p>Der MAPE zählt nur Tage, für die ein Fehler überhaupt definiert ist: offene Tage
 * und solche ganz ohne Ertrag bleiben draussen (siehe
 * {@link ForecastAccuracy#deviationPercent()}). Gibt es keinen einzigen, ist der MAPE
 * leer statt 0 – "kein Fehler messbar" und "Prognose perfekt" sind nicht dasselbe.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record AccuracyHistory(List<ForecastAccuracy> days, OptionalDouble mapePercent) {

    public AccuracyHistory {
        days = days == null ? List.of() : List.copyOf(days);
        if (mapePercent == null) {
            throw new IllegalArgumentException("mapePercent darf nicht null sein (leer statt null)");
        }
    }

    /** Bildet die Historie und rechnet den MAPE aus den bewertbaren Tagen. */
    public static AccuracyHistory of(List<ForecastAccuracy> days) {
        List<ForecastAccuracy> safe = days == null ? List.of() : List.copyOf(days);
        double sum = 0;
        int count = 0;
        for (ForecastAccuracy day : safe) {
            OptionalDouble deviation = day.deviationPercent();
            if (deviation.isPresent()) {
                sum += deviation.getAsDouble();
                count++;
            }
        }
        return new AccuracyHistory(safe, count == 0 ? OptionalDouble.empty() : OptionalDouble.of(sum / count));
    }

    /** Anzahl Tage, die in den MAPE eingeflossen sind – ohne sie ist er nicht einzuordnen. */
    public int ratedDays() {
        return (int) days.stream().filter(day -> day.deviationPercent().isPresent()).count();
    }
}
