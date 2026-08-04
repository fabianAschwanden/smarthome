package fabianaschwanden.smarthome.adapter.in.rest.dto.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.ChargeRecommendation;

/** Transport-Objekt der Ladeempfehlung: das beste Fenster plus seine Belastbarkeit. */
public record RecommendationDto(String from, String to, double expectedKwh, double peakWatt, String confidence) {

    public static RecommendationDto from(ChargeRecommendation recommendation) {
        SurplusWindowDto window = SurplusWindowDto.from(recommendation.window());
        return new RecommendationDto(
                window.from(), window.to(), window.expectedKwh(), window.peakWatt(),
                recommendation.confidence().name());
    }
}
