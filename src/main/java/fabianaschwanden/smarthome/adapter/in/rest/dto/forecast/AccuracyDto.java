package fabianaschwanden.smarthome.adapter.in.rest.dto.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.AccuracyHistory;
import fabianaschwanden.smarthome.domain.model.forecast.ForecastAccuracy;

import java.util.List;

/**
 * Transport-Objekt der Prognose-Genauigkeit.
 *
 * <p>{@code mapePercent} und {@code actualKwh} sind {@code null}, solange nichts
 * Bewertbares vorliegt – nicht 0. Eine Null hiesse «kein Fehler» bzw. «nichts
 * produziert»; beides wäre eine andere Aussage als «wissen wir noch nicht».
 * {@code ratedDays} sagt, auf wie vielen Tagen der Wert beruht.
 */
public record AccuracyDto(Double mapePercent, int ratedDays, List<DayDto> days) {

    public static AccuracyDto from(AccuracyHistory history) {
        return new AccuracyDto(
                history.mapePercent().isPresent() ? history.mapePercent().getAsDouble() : null,
                history.ratedDays(),
                history.days().stream().map(DayDto::from).toList());
    }

    public record DayDto(String date, double forecastKwh, Double actualKwh, Double deviationPercent) {

        public static DayDto from(ForecastAccuracy day) {
            return new DayDto(
                    day.date().toString(),
                    day.forecastKwh(),
                    day.actualKwh().isPresent() ? day.actualKwh().getAsDouble() : null,
                    day.deviationPercent().isPresent() ? day.deviationPercent().getAsDouble() : null);
        }
    }
}
