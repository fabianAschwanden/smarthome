package fabianaschwanden.smarthome.adapter.in.rest.dto.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.PvForecast;

import java.util.List;

/**
 * Transport-Objekt der PV-Prognose. {@code computedAt} und {@code learnedAt} gehen
 * bewusst mit ans Frontend: Bei ausgefallener Strahlungsquelle bleibt die letzte
 * Prognose stehen, und dann muss sichtbar sein, wie alt die Zahlen sind (SPEC §6).
 */
public record PvForecastDto(
        List<HourDto> hours,
        double todayKwh,
        double tomorrowKwh,
        String confidence,
        String learnedAt,
        String computedAt) {

    public static PvForecastDto from(PvForecast forecast) {
        return new PvForecastDto(
                forecast.hours().stream().map(HourDto::from).toList(),
                round(forecast.todayKwh()),
                round(forecast.tomorrowKwh()),
                forecast.confidence().name(),
                forecast.learnedAt() == null ? null : forecast.learnedAt().toString(),
                forecast.computedAt().toString());
    }

    /** Eine Stunde der Prognose; der Zeitpunkt markiert den Stundenbeginn. */
    public record HourDto(String hour, double expectedPvWatt, double gti) {

        public static HourDto from(PvForecast.HourEntry entry) {
            return new HourDto(
                    entry.hour().toString(),
                    round(entry.expectedPvWatt()),
                    round(entry.gtiWattPerSqm()));
        }
    }

    /** Eine Nachkommastelle reicht – mehr suggeriert eine Genauigkeit, die nicht da ist. */
    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
