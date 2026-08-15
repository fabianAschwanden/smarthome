package fabianaschwanden.smarthome.adapter.in.metrics;

import fabianaschwanden.smarthome.domain.model.sensor.Sensor;
import fabianaschwanden.smarthome.domain.port.in.sensor.ReadSensors;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Treiber-Adapter (Metrics): exponiert Innen- und Aussentemperatur samt Luftfeuchte als
 * Prometheus-Gauges unter {@code /q/metrics}.
 *
 * <p>Die Historie entsteht damit in Prometheus, nicht in der eigenen Datenbank – ein
 * zweiter Zeitreihen-Speicher neben dem, der ohnehin läuft, wäre Doppelarbeit. Die
 * Aufbewahrung richtet sich nach der Prometheus-Retention (derzeit 30 Tage).
 *
 * <p>Ein Messwert, der nicht vorliegt (Sensor offline oder Platzhalter der Domäne),
 * meldet {@code NaN} – in Grafana eine Lücke statt einer falschen Null. Ein
 * durchgezogener Strich auf 0 °C sähe aus wie Frost, nicht wie ein Ausfall.
 */
@Startup
@ApplicationScoped
public class SensorMetrics {

    /**
     * Ein Scrape liest mehrere Gauges; ohne Cache würde jeder einzelne die Geräte erneut
     * befragen. Kürzer als jedes sinnvolle Scrape-Intervall.
     */
    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    /** Unterhalb davon meldet die Domäne einen Platzhalter, keinen Messwert. */
    private static final double TEMPERATURE_UNKNOWN_BELOW = -100.0;

    private final ReadSensors sensors;
    private volatile Cached cached;

    SensorMetrics(MeterRegistry registry, ReadSensors sensors) {
        this.sensors = sensors;
        // Die Gauges entstehen einmal je bekanntem Sensor. Die Liste kommt aus der
        // Konfiguration und ist zur Laufzeit stabil; ein Sensor, der spaeter dazukommt,
        // braucht ohnehin einen Neustart.
        for (Sensor sensor : sensors.list()) {
            register(registry, sensor.id(), "smarthome.sensor.temperature.celsius",
                    "Gemessene Temperatur in Grad Celsius", SensorMetrics::temperatureOf);
            register(registry, sensor.id(), "smarthome.sensor.humidity.percent",
                    "Gemessene relative Luftfeuchte in Prozent", SensorMetrics::humidityOf);
        }
    }

    private void register(MeterRegistry registry, String id, String name, String description,
                          ToDoubleFunction<Sensor> value) {
        Gauge.builder(name, this, metrics -> metrics.read(id, value))
                .tag("sensor", id)
                .description(description)
                .register(registry);
    }

    /** Der Wert des Sensors, oder {@code NaN}, wenn er gerade nichts Belastbares liefert. */
    private double read(String id, ToDoubleFunction<Sensor> value) {
        return snapshot().stream()
                .filter(sensor -> sensor.id().equals(id))
                .filter(Sensor::online)
                .findFirst()
                .map(value::applyAsDouble)
                .orElse(Double.NaN);
    }

    private static double temperatureOf(Sensor sensor) {
        return sensor.temperature() > TEMPERATURE_UNKNOWN_BELOW ? sensor.temperature() : Double.NaN;
    }

    private static double humidityOf(Sensor sensor) {
        return sensor.humidity() >= 0 ? sensor.humidity() : Double.NaN;
    }

    private List<Sensor> snapshot() {
        Cached current = cached;
        Instant now = Instant.now();
        if (current != null && current.fetchedAt().plus(CACHE_TTL).isAfter(now)) {
            return current.sensors();
        }
        List<Sensor> fresh;
        try {
            fresh = sensors.list();
        } catch (RuntimeException e) {
            // Auch Fehlschlaege cachen: eine Geraetestoerung darf nicht pro Gauge erneut
            // in einen Timeout laufen, der Scrape selbst bleibt intakt.
            fresh = List.of();
        }
        cached = new Cached(now, fresh);
        return fresh;
    }

    private record Cached(Instant fetchedAt, List<Sensor> sensors) {
    }
}
