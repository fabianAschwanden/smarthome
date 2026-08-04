package fabianaschwanden.smarthome.adapter.in.rest.dto.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.SurplusWindow;

/** Transport-Objekt eines Überschussfensters; {@code to} ist exklusiv. */
public record SurplusWindowDto(String from, String to, double expectedKwh, double peakWatt) {

    public static SurplusWindowDto from(SurplusWindow window) {
        return new SurplusWindowDto(
                window.from().toString(),
                window.to().toString(),
                Math.round(window.expectedKwh() * 10.0) / 10.0,
                Math.round(window.peakSurplusWatt()));
    }
}
