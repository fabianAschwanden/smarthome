package fabianaschwanden.smarthome.domain.model.forecast;

import java.time.Instant;
import java.util.List;

/**
 * Eine Strahlungsreihe in Modulebene, aufgeteilt in gemessene Vergangenheit und
 * Vorhersage.
 *
 * <p>Beides kommt aus demselben Abruf: {@code past} speist das Lernen des Anlagenprofils
 * (Ist-Strahlung gegen Ist-Leistung), {@code forecast} die eigentliche Prognose. Dass die
 * Vergangenheit mitgeliefert wird, erspart eine eigene Strahlungs-Historie in der
 * Datenbank (SPEC §2.1).
 */
public record IrradianceSeries(
        List<IrradiancePoint> past, List<IrradiancePoint> forecast, Instant fetchedAt) {

    public IrradianceSeries {
        if (fetchedAt == null) {
            throw new IllegalArgumentException("fetchedAt darf nicht null sein");
        }
        past = past == null ? List.of() : List.copyOf(past);
        forecast = forecast == null ? List.of() : List.copyOf(forecast);
    }

    public boolean isEmpty() {
        return past.isEmpty() && forecast.isEmpty();
    }
}
