package fabianaschwanden.smarthome.adapter.in.metrics;

import fabianaschwanden.smarthome.domain.model.sensor.Sensor;
import fabianaschwanden.smarthome.domain.port.in.sensor.ReadSensors;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Sensor-Gauges. Fehlende Messwerte muessen NaN melden - in Grafana eine Luecke
 * statt einer falschen Null; ein durchgezogener Strich auf 0 °C saehe aus wie Frost.
 *
 * <p>{@code @QuarkusTest}, damit die Coverage ins Quarkus-JaCoCo zaehlt.
 */
@QuarkusTest
class SensorMetricsTest {

    private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");

    private final MeterRegistry registry = new SimpleMeterRegistry();

    /**
     * Haelt den Adapter am Leben: Micrometer referenziert das an {@code Gauge.builder}
     * uebergebene Objekt nur schwach - wird es sonst nirgends gehalten, sammelt die GC es
     * ein und der Gauge liefert NaN, ohne die Messfunktion je aufzurufen.
     */
    @SuppressWarnings("unused")
    private SensorMetrics metrics;

    private double gauge(String name, String sensor) {
        return registry.get(name).tag("sensor", sensor).gauge().value();
    }

    private void build(Sensor... sensors) {
        metrics = new SensorMetrics(registry, (ReadSensors) () -> List.of(sensors));
    }

    @Test
    void meldet_temperatur_und_feuchte_je_sensor() {
        build(new Sensor("innen", "Innen", "Wohnzimmer", 23.4, 58, true, NOW),
                new Sensor("aussen", "Aussen", "Garten", 26.1, 66, true, NOW));

        assertEquals(23.4, gauge("smarthome.sensor.temperature.celsius", "innen"));
        assertEquals(58.0, gauge("smarthome.sensor.humidity.percent", "innen"));
        assertEquals(26.1, gauge("smarthome.sensor.temperature.celsius", "aussen"));
    }

    @Test
    void meldet_einen_offline_sensor_als_luecke() {
        build(new Sensor("innen", "Innen", "Wohnzimmer", 23.4, 58, false, NOW));

        assertTrue(Double.isNaN(gauge("smarthome.sensor.temperature.celsius", "innen")));
    }

    @Test
    void gibt_den_platzhalter_der_domaene_nicht_als_messwert_aus() {
        // -1000 °C und -1 % bedeuten "unbekannt", nicht "gemessen".
        build(new Sensor("innen", "Innen", "Wohnzimmer", -1000.0, -1, true, NOW));

        assertTrue(Double.isNaN(gauge("smarthome.sensor.temperature.celsius", "innen")));
        assertTrue(Double.isNaN(gauge("smarthome.sensor.humidity.percent", "innen")));
    }

    @Test
    void haelt_eine_geraetestoerung_vom_scrape_fern() {
        metrics = new SensorMetrics(registry, new ReadSensors() {
            private boolean first = true;

            @Override
            public List<Sensor> list() {
                if (first) {
                    first = false;  // Aufbau gelingt, danach faellt die Quelle aus
                    return List.of(new Sensor("innen", "Innen", "Wohnzimmer", 23.4, 58, true, NOW));
                }
                throw new IllegalStateException("Sensor nicht erreichbar");
            }
        });

        assertTrue(Double.isNaN(gauge("smarthome.sensor.temperature.celsius", "innen")));
    }
}
