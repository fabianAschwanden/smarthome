package fabianaschwanden.smarthome.adapter.in.metrics;

import fabianaschwanden.smarthome.domain.model.battery.BatteryControl;
import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.energy.EnergySnapshot;
import fabianaschwanden.smarthome.domain.model.energy.PowerReading;
import fabianaschwanden.smarthome.domain.model.energy.PowerSource;
import fabianaschwanden.smarthome.domain.port.in.battery.ControlBattery;
import fabianaschwanden.smarthome.domain.port.in.energy.CurrentEnergyQuery;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testet den Metrics-Adapter mit Fake-Ports und einer {@code SimpleMeterRegistry} –
 * keine Geräte, kein Prometheus. Verifiziert die Gauge-Werte, das NaN-Verhalten
 * bei Störungen und dass ein Scrape die Geräte nur einmal befragt (Cache).
 *
 * <p>{@code @QuarkusTest}, damit die Coverage ins Quarkus-JaCoCo zählt.
 */
@QuarkusTest
class EnergyMetricsTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

    private final FixedEnergyQuery energyQuery = new FixedEnergyQuery();
    private final FixedBattery battery = new FixedBattery();
    private final MeterRegistry registry = new SimpleMeterRegistry();

    /**
     * Hält den Adapter am Leben. Micrometer referenziert das über
     * {@code Gauge.builder(name, this, …)} übergebene Objekt nur <b>schwach</b>: Wird
     * die Instanz sonst nirgends gehalten, darf die GC sie einsammeln, und der Gauge
     * liefert danach {@code NaN}, <b>ohne</b> die Messfunktion je aufzurufen. Die
     * NaN-Erwartungen blieben dabei erfüllt – auffliegen würde es nur an der Zahl der
     * Abfragen, und das rein zufällig. In Produktion hält der CDI-Container die
     * {@code @ApplicationScoped}-Bean; im Test müssen wir das selbst tun.
     */
    @SuppressWarnings("unused")
    private EnergyMetrics metrics;

    private double gauge(String name, PowerSource source) {
        return registry.get(name)
                .tag("source", source.name().toLowerCase(java.util.Locale.ROOT))
                .gauge().value();
    }

    @Test
    void gauges_liefern_werte_der_erfolgreichen_quelle() {
        energyQuery.snapshot = new EnergySnapshot(NOW, List.of(
                PowerReading.of(PowerSource.FRONIUS, NOW, -300.0, 5000.0, null, 1200.0),
                PowerReading.error(PowerSource.SMARTFOX, NOW)), Optional.empty());
        metrics = new EnergyMetrics(registry, energyQuery, battery);

        assertEquals(5000.0, gauge("smarthome.pv.watt", PowerSource.FRONIUS));
        assertEquals(1200.0, gauge("smarthome.consumption.watt", PowerSource.FRONIUS));
        assertEquals(-300.0, gauge("smarthome.grid.watt", PowerSource.FRONIUS));
        // Fehlmessung = NaN (Lücke in Grafana), nicht 0 (sähe wie "keine Produktion" aus):
        assertTrue(Double.isNaN(gauge("smarthome.pv.watt", PowerSource.SMARTFOX)));
    }

    @Test
    void relais_zustand_als_null_oder_eins() {
        energyQuery.snapshot = new EnergySnapshot(NOW, List.of(), Optional.empty());
        metrics = new EnergyMetrics(registry, energyQuery, battery);

        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.ON, NOW);
        assertEquals(1.0, registry.get("smarthome.battery.relay.state").gauge().value());

        battery.control = new BatteryControl(ControlMode.MANUAL, RelayState.OFF, NOW);
        assertEquals(0.0, registry.get("smarthome.battery.relay.state").gauge().value());
    }

    @Test
    void ein_scrape_befragt_die_geraete_nur_einmal() {
        energyQuery.snapshot = new EnergySnapshot(NOW, List.of(
                PowerReading.of(PowerSource.FRONIUS, NOW, 0.0, 4200.0, null, 800.0)), Optional.empty());
        metrics = new EnergyMetrics(registry, energyQuery, battery);

        gauge("smarthome.pv.watt", PowerSource.FRONIUS);
        gauge("smarthome.consumption.watt", PowerSource.FRONIUS);
        gauge("smarthome.grid.watt", PowerSource.FRONIUS);

        assertEquals(1, energyQuery.calls);
    }

    @Test
    void geraetestoerung_ergibt_nan_statt_scrape_fehler() {
        energyQuery.snapshot = null; // Query wirft
        metrics = new EnergyMetrics(registry, energyQuery, battery);

        assertTrue(Double.isNaN(gauge("smarthome.pv.watt", PowerSource.FRONIUS)));
        assertTrue(Double.isNaN(gauge("smarthome.consumption.watt", PowerSource.SMARTFOX)));
        // Nur ein Fehlversuch trotz mehrerer Gauge-Reads (Fehlschlag wird gecacht):
        assertEquals(1, energyQuery.calls);
    }

    /** Liefert den gesetzten Snapshot; ohne Snapshot wirft er wie eine gestörte Quelle. */
    private static final class FixedEnergyQuery implements CurrentEnergyQuery {
        private EnergySnapshot snapshot;
        private int calls;

        @Override
        public EnergySnapshot currentEnergy() {
            calls++;
            if (snapshot == null) {
                throw new IllegalStateException("Quelle nicht erreichbar");
            }
            return snapshot;
        }
    }

    private static final class FixedBattery implements ControlBattery {
        private BatteryControl control = new BatteryControl(ControlMode.MANUAL, RelayState.OFF, NOW);

        @Override
        public BatteryControl status() {
            return control;
        }

        @Override
        public BatteryControl changeMode(ControlMode mode) {
            throw new UnsupportedOperationException("im Test nicht benötigt");
        }

        @Override
        public BatteryControl switchRelay(RelayState state) {
            throw new UnsupportedOperationException("im Test nicht benötigt");
        }
    }
}
