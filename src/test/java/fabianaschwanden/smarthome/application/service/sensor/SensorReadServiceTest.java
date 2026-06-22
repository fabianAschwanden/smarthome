package fabianaschwanden.smarthome.application.service.sensor;

import fabianaschwanden.smarthome.domain.model.sensor.Sensor;
import fabianaschwanden.smarthome.domain.port.out.sensor.SensorDevice;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testet die Lese-/Offline-Logik des SensorReadService mit Fake-Geräten. */
@QuarkusTest
class SensorReadServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-22T10:00:00Z"), ZoneOffset.UTC);

    /** Fake-Sensor mit umschaltbarer Erreichbarkeit. */
    private static final class FakeSensor implements SensorDevice {
        private final String id;
        private Optional<Reading> reading;

        FakeSensor(String id, Optional<Reading> reading) {
            this.id = id;
            this.reading = reading;
        }

        @Override public String id() { return id; }
        @Override public String name() { return "Sensor " + id; }
        @Override public String room() { return "Wohnzimmer"; }
        @Override public Optional<Reading> read() { return reading; }
    }

    @Test
    void erreichbarerSensorLiefertOnlineMitWerten() {
        FakeSensor s = new FakeSensor("innen", Optional.of(new SensorDevice.Reading(21.5, 48)));
        SensorReadService service = new SensorReadService(List.of(s), CLOCK);

        List<Sensor> list = service.list();
        assertEquals(1, list.size());
        Sensor sensor = list.get(0);
        assertTrue(sensor.online());
        assertEquals(21.5, sensor.temperature());
        assertEquals(48, sensor.humidity());
        assertEquals("innen", sensor.id());
    }

    @Test
    void nichtErreichbarerSensorOhneHistorieIstOfflineMitUnknown() {
        FakeSensor s = new FakeSensor("innen", Optional.empty());
        SensorReadService service = new SensorReadService(List.of(s), CLOCK);

        Sensor sensor = service.list().get(0);
        assertFalse(sensor.online());
        assertEquals(Sensor.VALUE_UNKNOWN, sensor.temperature());
        assertEquals(Sensor.HUMIDITY_UNKNOWN, sensor.humidity());
    }

    @Test
    void offlineSensorBehaeltLetzteWerte() {
        FakeSensor s = new FakeSensor("innen", Optional.of(new SensorDevice.Reading(22.0, 50)));
        SensorReadService service = new SensorReadService(List.of(s), CLOCK);

        // Erst online lesen (füllt lastKnown), dann offline schalten.
        assertTrue(service.list().get(0).online());
        s.reading = Optional.empty();

        Sensor sensor = service.list().get(0);
        assertFalse(sensor.online());
        assertEquals(22.0, sensor.temperature());
        assertEquals(50, sensor.humidity());
    }
}
