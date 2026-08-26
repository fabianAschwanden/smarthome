package fabianaschwanden.smarthome.domain.service.charging;

import fabianaschwanden.smarthome.domain.model.charging.ChargingSession;
import fabianaschwanden.smarthome.domain.model.energy.EnergySample;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Schätzt die Ladeenergie aus dem Verbrauchssprung beim Einschalten.
 *
 * <p><b>Warum überhaupt geschätzt wird:</b> Weder SMARTFOX noch Wechselrichter messen das
 * Lade-Relais separat – es gibt keinen kWh-Zähler dafür. Was bleibt, ist der Hausverbrauch,
 * und darin ist das Ladegerät enthalten. Weil die App selbst schaltet, kennt sie den
 * Zeitpunkt sekundengenau: Der Sprung im Verbrauch unmittelbar danach <em>ist</em> die
 * Ladeleistung.
 *
 * <p>Gerechnet wird mit dem <b>Median</b> statt dem Mittelwert, davor wie danach: Ein
 * einzelner Ausreisser – der Backofen, der zufällig zur selben Sekunde anspringt – würde
 * einen Mittelwert verschieben, den Median kaum.
 *
 * <p>Nach dem Einschalten wird eine Einschwingzeit übersprungen; Ladegeräte fahren ihre
 * Leistung nicht schlagartig hoch, und die ersten Sekunden wären zu niedrig.
 *
 * <p><b>Was die Schätzung nicht kann:</b> Schaltet gleichzeitig eine andere grosse Last
 * ein oder aus, wandert deren Leistung in die Rechnung. Und sie nimmt die Ladeleistung als
 * über den ganzen Vorgang konstant an – ein Ladegerät, das gegen Ende abregelt, wird
 * überschätzt. Reine Funktion, keine Uhr, kein Zustand.
 */
public class ChargingEnergyEstimator {

    private static final double WATT_TO_KW = 1000.0;
    private static final double SECONDS_PER_HOUR = 3600.0;

    /**
     * Schätzt einen Ladevorgang.
     *
     * @param samples        Messpunkte, die den Zeitraum davor und währenddessen abdecken
     * @param startedAt      Einschaltzeitpunkt
     * @param endedAt        Ausschaltzeitpunkt
     * @param settleTime     Einschwingzeit, die nach dem Einschalten übersprungen wird
     * @param baselineWindow wie weit vor dem Einschalten der Vergleich gezogen wird
     * @return leer, wenn zu wenige Messpunkte vorliegen, um überhaupt zu vergleichen
     */
    public Optional<ChargingSession> estimate(
            List<EnergySample> samples,
            Instant startedAt,
            Instant endedAt,
            Duration settleTime,
            Duration baselineWindow) {

        if (samples == null || startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) {
            return Optional.empty();
        }
        Instant settled = startedAt.plus(settleTime);
        List<Double> before = consumptionIn(samples, startedAt.minus(baselineWindow), startedAt);
        List<Double> during = consumptionIn(samples, settled, endedAt);
        if (before.isEmpty() || during.isEmpty()) {
            // Ohne Vergleichswerte gibt es keine Schaetzung. Eine 0 auszuweisen waere
            // schlimmer als gar nichts: Sie saehe aus wie "nicht geladen".
            return Optional.empty();
        }

        // Negatives kann es nicht geben - faellt der Verbrauch beim Einschalten, hat eine
        // andere Last aufgehoert, und ueber das Ladegeraet sagt das nichts.
        double watt = Math.max(0, median(during) - median(before));
        double hours = Duration.between(startedAt, endedAt).toSeconds() / SECONDS_PER_HOUR;
        double kwh = watt * hours / WATT_TO_KW;
        return Optional.of(new ChargingSession(startedAt, endedAt, round(watt), round(kwh)));
    }

    private static List<Double> consumptionIn(List<EnergySample> samples, Instant from, Instant to) {
        List<Double> values = new ArrayList<>();
        for (EnergySample sample : samples) {
            Instant ts = sample.timestamp();
            if (!ts.isBefore(from) && ts.isBefore(to)) {
                values.add(sample.consumptionWatt());
            }
        }
        return values;
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    /** Zwei Nachkommastellen – mehr Genauigkeit täuscht die Schätzung nur vor. */
    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
