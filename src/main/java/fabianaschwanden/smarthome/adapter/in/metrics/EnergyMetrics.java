package fabianaschwanden.smarthome.adapter.in.metrics;

import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.energy.EnergySnapshot;
import fabianaschwanden.smarthome.domain.model.energy.PowerReading;
import fabianaschwanden.smarthome.domain.model.energy.PowerSource;
import fabianaschwanden.smarthome.domain.port.in.battery.ControlBattery;
import fabianaschwanden.smarthome.domain.port.in.energy.CurrentEnergyQuery;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * Treiber-Adapter (Metrics): exponiert Energie-Momentwerte und den Batterie-
 * Relais-Zustand als Prometheus-Gauges unter {@code /q/metrics} — für das
 * Grafana-Board "Haus + Server" (onprem-infrastructure, docs/MONITORING.md §5).
 *
 * <p>Gauges sind pull-basiert: erst der Prometheus-Scrape löst die Abfrage aus.
 * Fehlt eine gültige Messung (Quelle down, Wert nicht geliefert), meldet der
 * Gauge {@code NaN} — in Grafana eine Lücke statt einer falschen Null.
 */
@Startup
@ApplicationScoped
public class EnergyMetrics {

    /**
     * Ein Scrape liest mehrere Gauges; ohne Cache würde jeder einzelne die
     * Geräte erneut befragen. Kürzer als jedes sinnvolle Scrape-Intervall.
     */
    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final CurrentEnergyQuery currentEnergy;
    private final ControlBattery battery;
    private volatile Cached cached;

    EnergyMetrics(MeterRegistry registry, CurrentEnergyQuery currentEnergy, ControlBattery battery) {
        this.currentEnergy = currentEnergy;
        this.battery = battery;
        for (PowerSource source : PowerSource.values()) {
            registerPerSource(registry, source, "smarthome.pv.watt",
                    "PV-Produktion in Watt", PowerReading::pvWatt);
            registerPerSource(registry, source, "smarthome.consumption.watt",
                    "Hausverbrauch in Watt", PowerReading::consumptionWatt);
            registerPerSource(registry, source, "smarthome.grid.watt",
                    "Netzbezug (+) bzw. Einspeisung (-) in Watt", PowerReading::gridWatt);
        }
        Gauge.builder("smarthome.battery.relay.state", this, EnergyMetrics::relayState)
                .description("Von der Steuerung gewuenschter Relais-Zustand (1 = ON, 0 = OFF)")
                .register(registry);
    }

    private void registerPerSource(MeterRegistry registry, PowerSource source,
                                   String name, String description, ToDoubleFunction<PowerReading> value) {
        Gauge.builder(name, this, metrics -> metrics.read(source, value))
                .tag("source", source.name().toLowerCase(Locale.ROOT))
                .description(description)
                .register(registry);
    }

    private double read(PowerSource source, ToDoubleFunction<PowerReading> value) {
        EnergySnapshot snapshot = snapshot();
        if (snapshot == null) {
            return Double.NaN;
        }
        return snapshot.readings().stream()
                .filter(reading -> reading.source() == source && reading.isOk())
                .findFirst()
                .map(value::applyAsDouble)
                .orElse(Double.NaN);
    }

    private double relayState() {
        try {
            return battery.status().desiredState() == RelayState.ON ? 1.0 : 0.0;
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    private EnergySnapshot snapshot() {
        Cached current = cached;
        Instant now = Instant.now();
        if (current != null && current.fetchedAt().plus(CACHE_TTL).isAfter(now)) {
            return current.snapshot();
        }
        EnergySnapshot fresh;
        try {
            fresh = currentEnergy.currentEnergy();
        } catch (RuntimeException e) {
            // Auch Fehlschläge cachen: eine Gerätestörung darf nicht pro Gauge
            // erneut in einen Timeout laufen, der Scrape selbst bleibt intakt.
            fresh = null;
        }
        cached = new Cached(now, fresh);
        return fresh;
    }

    private record Cached(Instant fetchedAt, EnergySnapshot snapshot) {
    }
}
