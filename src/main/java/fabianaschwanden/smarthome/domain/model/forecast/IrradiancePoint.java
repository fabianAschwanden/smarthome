package fabianaschwanden.smarthome.domain.model.forecast;

import java.time.Instant;

/**
 * Strahlung in Modulebene (GTI) für eine volle Stunde – entweder prognostiziert oder,
 * aus {@code past_days}, gemessen. Der Zeitpunkt markiert den Beginn der Stunde.
 */
public record IrradiancePoint(Instant hour, double gtiWattPerSqm) {

    public IrradiancePoint {
        if (hour == null) {
            throw new IllegalArgumentException("hour darf nicht null sein");
        }
        if (gtiWattPerSqm < 0) {
            throw new IllegalArgumentException("gtiWattPerSqm darf nicht negativ sein: " + gtiWattPerSqm);
        }
    }
}
