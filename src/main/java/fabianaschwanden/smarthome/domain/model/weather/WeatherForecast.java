package fabianaschwanden.smarthome.domain.model.weather;

import java.time.Instant;
import java.util.List;

/**
 * Wettervorhersage für einen Ort: aktueller Zustand, Tages-Hoch/Tief und ein
 * Stundenverlauf. Reines Domänen-Record (Value Object), Invarianten im Compact-Constructor.
 */
public record WeatherForecast(
        String location,
        double currentTemp,
        WeatherCondition condition,
        double dayMax,
        double dayMin,
        List<HourEntry> hours,
        Instant observedAt) {

    public WeatherForecast {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location darf nicht leer sein");
        }
        if (condition == null) {
            throw new IllegalArgumentException("condition darf nicht null sein");
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt darf nicht null sein");
        }
        hours = hours == null ? List.of() : List.copyOf(hours);
    }

    /** Ein Stundeneintrag des Verlaufs. */
    public record HourEntry(String label, double temp, WeatherCondition condition) {
    }
}
