package fabianaschwanden.smarthome.domain.model.forecast;

import java.time.Duration;
import java.time.Instant;

/**
 * Ein zusammenhängender Zeitraum, in dem mehr PV-Leistung erwartet wird als das Haus
 * voraussichtlich braucht. {@code to} ist exklusiv – ein Fenster 11–15 Uhr umfasst die
 * Stunden 11, 12, 13 und 14.
 */
public record SurplusWindow(Instant from, Instant to, double expectedKwh, double peakSurplusWatt) {

    public SurplusWindow {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from und to dürfen nicht null sein");
        }
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("to muss nach from liegen: " + from + " -> " + to);
        }
        if (expectedKwh < 0) {
            throw new IllegalArgumentException("expectedKwh darf nicht negativ sein: " + expectedKwh);
        }
        if (peakSurplusWatt < 0) {
            throw new IllegalArgumentException("peakSurplusWatt darf nicht negativ sein: " + peakSurplusWatt);
        }
    }

    public Duration duration() {
        return Duration.between(from, to);
    }
}
