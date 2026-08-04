package fabianaschwanden.smarthome.adapter.in.rest.dto.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.ChargeRecommendation;
import fabianaschwanden.smarthome.domain.model.forecast.ConsumptionBaseline;
import fabianaschwanden.smarthome.domain.model.forecast.SurplusWindow;

import java.util.List;
import java.util.Optional;

/**
 * Transport-Objekt der Überschuss-Sicht: die Vergleichskurve des typischen Verbrauchs,
 * alle Fenster und – falls vorhanden – die Empfehlung.
 *
 * <p>{@code recommendation} ist {@code null}, wenn kein Fenster die Schwellen erreicht
 * (etwa an einem trüben Tag). Das ist ein normaler Zustand, kein Fehler.
 */
public record SurplusDto(
        List<Double> baselineWeekdayWatt,
        List<Double> baselineWeekendWatt,
        List<SurplusWindowDto> windows,
        RecommendationDto recommendation) {

    public static SurplusDto from(
            Optional<ConsumptionBaseline> baseline,
            List<SurplusWindow> windows,
            Optional<ChargeRecommendation> recommendation) {
        return new SurplusDto(
                baseline.map(ConsumptionBaseline::weekdayWatt).orElse(List.of()),
                baseline.map(ConsumptionBaseline::weekendWatt).orElse(List.of()),
                windows.stream().map(SurplusWindowDto::from).toList(),
                recommendation.map(RecommendationDto::from).orElse(null));
    }
}
