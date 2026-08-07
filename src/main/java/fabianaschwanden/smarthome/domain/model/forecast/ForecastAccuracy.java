package fabianaschwanden.smarthome.domain.model.forecast;

import java.time.LocalDate;
import java.util.OptionalDouble;

/**
 * Was für einen Tag vorhergesagt wurde und was tatsächlich kam.
 *
 * <p>Ein Eintrag entsteht am Morgen mit der Prognose und wird nach Tagesende um den
 * Ist-Wert ergänzt. Bis dahin ist {@code actualKwh} leer – bewusst {@link OptionalDouble}
 * und kein Platzhalter-Zahlenwert: Ein noch offener Tag darf in keiner Auswertung als
 * "0 kWh produziert" durchgehen.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record ForecastAccuracy(LocalDate date, double forecastKwh, OptionalDouble actualKwh) {

    public ForecastAccuracy {
        if (date == null) {
            throw new IllegalArgumentException("date darf nicht null sein");
        }
        if (forecastKwh < 0) {
            throw new IllegalArgumentException("forecastKwh darf nicht negativ sein: " + forecastKwh);
        }
        if (actualKwh == null) {
            throw new IllegalArgumentException("actualKwh darf nicht null sein (leer statt null)");
        }
        if (actualKwh.isPresent() && actualKwh.getAsDouble() < 0) {
            throw new IllegalArgumentException("actualKwh darf nicht negativ sein: " + actualKwh.getAsDouble());
        }
    }

    /** Ein Tag mit Prognose, dessen Ist-Wert noch aussteht. */
    public static ForecastAccuracy predicted(LocalDate date, double forecastKwh) {
        return new ForecastAccuracy(date, forecastKwh, OptionalDouble.empty());
    }

    /** Derselbe Tag mit eingetragenem Ist-Wert. */
    public ForecastAccuracy settledWith(double actual) {
        return new ForecastAccuracy(date, forecastKwh, OptionalDouble.of(actual));
    }

    public boolean isSettled() {
        return actualKwh.isPresent();
    }

    /**
     * Relativer Fehler in Prozent, bezogen auf den Ist-Wert (der Anteil des Tages, den
     * die Prognose danebenlag).
     *
     * <p>Leer, solange der Tag offen ist – und auch dann, wenn tatsächlich nichts
     * produziert wurde: Bei einem Ist-Wert von 0 wäre der relative Fehler nicht
     * definiert. Ihn auf 0 oder 100 % zu setzen wäre eine erfundene Zahl, und ein
     * einziger ertragloser Tag würde jeden Durchschnitt darüber verzerren.
     */
    public OptionalDouble deviationPercent() {
        if (actualKwh.isEmpty() || actualKwh.getAsDouble() == 0.0) {
            return OptionalDouble.empty();
        }
        double actual = actualKwh.getAsDouble();
        return OptionalDouble.of(Math.abs(forecastKwh - actual) / actual * 100.0);
    }
}
