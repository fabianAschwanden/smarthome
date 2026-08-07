package fabianaschwanden.smarthome.domain.service.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.HeatProtectionWindow;
import fabianaschwanden.smarthome.domain.model.forecast.PvForecast;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Sucht in der Strahlungsprognose das zusammenhängende Fenster, in dem Beschatten lohnt.
 *
 * <p>Reine Funktion ohne Zustand und ohne Uhr – alles, was sie braucht, bekommt sie
 * übergeben.
 */
public class HeatProtectionPlanner {

    /** Eine Stunde ist der Raster der Prognose. */
    private static final Duration HOUR = Duration.ofHours(1);

    /**
     * Das nächste Beschattungsfenster ab {@code now}.
     *
     * <p>Leer, wenn eine der beiden Bedingungen fehlt: Die Räume müssen bereits warm sein
     * <em>und</em> die Einstrahlung muss die Schwelle erreichen. Ebenfalls leer, wenn das
     * Fenster kürzer wäre als {@code minDuration} – für zwanzig Minuten Sonne die Storen
     * zu fahren, kostet mehr Aufmerksamkeit, als es Wärme spart.
     *
     * @param indoorTemp aktuelle Innentemperatur in °C
     */
    public Optional<HeatProtectionWindow> plan(
            PvForecast forecast,
            double indoorTemp,
            Instant now,
            double gtiThreshold,
            double indoorTempThreshold,
            Duration minDuration) {

        if (forecast == null || indoorTemp < indoorTempThreshold) {
            return Optional.empty();
        }

        List<PvForecast.HourEntry> hours = forecast.hours().stream()
                .filter(hour -> !hour.hour().plus(HOUR).isBefore(now))
                .sorted((a, b) -> a.hour().compareTo(b.hour()))
                .toList();

        Instant start = null;
        Instant end = null;
        double peak = 0;
        for (PvForecast.HourEntry hour : hours) {
            boolean hot = hour.gtiWattPerSqm() >= gtiThreshold;
            if (hot && start == null) {
                start = hour.hour();
            }
            if (hot) {
                end = hour.hour().plus(HOUR);
                peak = Math.max(peak, hour.gtiWattPerSqm());
            } else if (start != null) {
                // Erste Lücke beendet das Fenster: Ein zweites Hoch am Nachmittag ist
                // ein eigenes Fenster und keine Verlängerung über die Wolken hinweg.
                break;
            }
        }

        if (start == null) {
            return Optional.empty();
        }
        // Läuft das Fenster bereits, beginnt es jetzt - rückwirkend beschatten geht nicht.
        Instant effectiveStart = start.isBefore(now) ? now : start;
        if (Duration.between(effectiveStart, end).compareTo(minDuration) < 0) {
            return Optional.empty();
        }
        return Optional.of(new HeatProtectionWindow(effectiveStart, end, peak, indoorTemp));
    }
}
