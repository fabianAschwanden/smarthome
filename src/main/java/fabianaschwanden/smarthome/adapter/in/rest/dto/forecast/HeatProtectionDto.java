package fabianaschwanden.smarthome.adapter.in.rest.dto.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.HeatProtectionWindow;

/**
 * Transport-Objekt des Beschattungsfensters.
 *
 * <p>{@code shadedPosition} ist die Geräteposition (0 = zu, 100 = offen);
 * {@code closedPercent} dieselbe Angabe in der Sprache der Oberfläche («% zu»). Beides
 * mitzugeben ist kein Luxus – die zwei Skalen sind die häufigste Verwechslung in diesem
 * Bereich, und das Frontend soll nicht selbst rechnen müssen.
 */
public record HeatProtectionDto(
        String from,
        String to,
        double peakGti,
        double indoorTemp,
        int shadedPosition,
        int closedPercent) {

    public static HeatProtectionDto from(HeatProtectionWindow window, int shadedPosition) {
        return new HeatProtectionDto(
                window.from().toString(),
                window.to().toString(),
                window.peakGti(),
                window.indoorTemp(),
                shadedPosition,
                100 - shadedPosition);
    }
}
