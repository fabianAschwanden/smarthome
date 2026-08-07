package fabianaschwanden.smarthome.domain.port.out.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.ForecastAccuracy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Getriebener Port: speichert und liest den Vergleich Prognose gegen Ist je Tag. */
public interface ForecastAccuracyRepository {

    /** Legt den Eintrag an oder aktualisiert ihn (Schlüssel ist das Datum). */
    void save(ForecastAccuracy accuracy);

    Optional<ForecastAccuracy> byDate(LocalDate date);

    /** Die letzten {@code limit} Tage, absteigend nach Datum (neuester zuerst). */
    List<ForecastAccuracy> latest(int limit);
}
