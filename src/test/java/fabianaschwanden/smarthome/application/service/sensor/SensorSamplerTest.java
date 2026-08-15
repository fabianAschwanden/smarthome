package fabianaschwanden.smarthome.application.service.sensor;

import fabianaschwanden.smarthome.domain.model.sensor.Sensor;
import fabianaschwanden.smarthome.domain.model.sensor.SensorSample;
import fabianaschwanden.smarthome.domain.port.in.sensor.ReadSensors;
import fabianaschwanden.smarthome.domain.port.out.sensor.SensorSampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Was aufgezeichnet wird - und was bewusst nicht. */
class SensorSamplerTest {

    private static final Instant JETZT = Instant.parse("2026-08-10T12:00:00Z");
    private static final Clock UHR = Clock.fixed(JETZT, ZoneOffset.UTC);

    private FakeSensors sensors;
    private FakeRepository repository;
    private SensorSampler sampler;

    @BeforeEach
    void setUp() {
        sensors = new FakeSensors();
        repository = new FakeRepository();
        sampler = new SensorSampler(sensors, repository, UHR, 365);
    }

    @Test
    void zeichnet_jeden_erreichbaren_sensor_auf() {
        sensors.sensors = List.of(
                new Sensor("innen", "Innen", "Wohnzimmer", 23.4, 58, true, JETZT),
                new Sensor("aussen", "Aussen", "Garten", 26.1, 66, true, JETZT));

        sampler.sample();

        assertEquals(2, repository.saved.size());
        assertEquals("innen", repository.saved.get(0).sensorId());
        assertEquals(23.4, repository.saved.get(0).temperature());
        assertEquals(JETZT, repository.saved.get(0).timestamp());
    }

    @Test
    void zeichnet_einen_offline_sensor_nicht_auf() {
        // Eine Luecke ist ehrlicher als eine erfundene Zahl.
        sensors.sensors = List.of(new Sensor("innen", "Innen", "Wohnzimmer", 23.4, 58, false, JETZT));

        sampler.sample();

        assertTrue(repository.saved.isEmpty());
    }

    @Test
    void zeichnet_den_platzhalter_der_domaene_nicht_auf() {
        // -1000 °C waere in jeder Auswertung ein Ausreisser, der alles verzerrt.
        sensors.sensors = List.of(
                new Sensor("innen", "Innen", "Wohnzimmer", Sensor.VALUE_UNKNOWN, 58, true, JETZT));

        sampler.sample();

        assertTrue(repository.saved.isEmpty());
    }

    @Test
    void verdichtet_ab_der_konfigurierten_grenze() {
        sampler.compact();

        assertEquals(JETZT.minus(java.time.Duration.ofDays(365)), repository.compactedBefore);
    }

    private static final class FakeSensors implements ReadSensors {
        private List<Sensor> sensors = List.of();

        @Override
        public List<Sensor> list() {
            return sensors;
        }
    }

    private static final class FakeRepository implements SensorSampleRepository {
        private final List<SensorSample> saved = new ArrayList<>();
        private Instant compactedBefore;

        @Override
        public void save(SensorSample sample) {
            saved.add(sample);
        }

        @Override
        public List<SensorSample> between(Instant fromInclusive, Instant toExclusive) {
            return saved;
        }

        @Override
        public long compactOlderThan(Instant cutoff) {
            compactedBefore = cutoff;
            return 0;
        }

        @Override
        public long total() {
            return saved.size();
        }
    }
}
