package fabianaschwanden.smarthome.domain.model.forecast;

import java.time.Instant;
import java.util.List;

/**
 * Die erwartete PV-Leistung je Stunde für heute und morgen, samt Tagessummen.
 *
 * <p>{@code learnedAt} sagt, wie alt das zugrunde liegende Anlagenprofil ist,
 * {@code computedAt}, wann diese Prognose gerechnet wurde. Beides steht im UI, weil die
 * App bei fehlender Strahlungsquelle bewusst die letzte Prognose stehen lässt statt
 * auszufallen – dann muss sichtbar sein, dass die Zahlen altern (SPEC §6).
 */
public record PvForecast(
        List<HourEntry> hours,
        double todayKwh,
        double tomorrowKwh,
        Confidence confidence,
        Instant learnedAt,
        Instant computedAt) {

    public PvForecast {
        if (todayKwh < 0 || tomorrowKwh < 0) {
            throw new IllegalArgumentException("Tagessummen dürfen nicht negativ sein");
        }
        if (confidence == null) {
            throw new IllegalArgumentException("confidence darf nicht null sein");
        }
        if (computedAt == null) {
            throw new IllegalArgumentException("computedAt darf nicht null sein");
        }
        hours = hours == null ? List.of() : List.copyOf(hours);
    }

    /** Erwartete Leistung einer Stunde; der Zeitpunkt markiert den Stundenbeginn. */
    public record HourEntry(Instant hour, double expectedPvWatt, double gtiWattPerSqm) {

        public HourEntry {
            if (hour == null) {
                throw new IllegalArgumentException("hour darf nicht null sein");
            }
            if (expectedPvWatt < 0) {
                throw new IllegalArgumentException("expectedPvWatt darf nicht negativ sein: " + expectedPvWatt);
            }
        }
    }
}
