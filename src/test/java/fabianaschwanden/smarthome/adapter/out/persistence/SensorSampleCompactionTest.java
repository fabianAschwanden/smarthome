package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.model.sensor.SensorSample;
import fabianaschwanden.smarthome.domain.port.out.sensor.SensorSampleRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Die Verdichtung gegen die echte Datenbank - sie ist als SQL formuliert und damit von
 * einer Attrappe nicht pruefbar.
 */
@QuarkusTest
class SensorSampleCompactionTest {

    private static final Instant BASIS = Instant.parse("2024-03-05T08:00:00Z");

    @Inject
    SensorSampleRepository repository;

    @Test
    @TestTransaction
    void behaelt_je_stunde_und_sensor_einen_punkt() {
        // Zwei Sensoren, je sechs Punkte in zwei Stunden (alle 20 Minuten).
        for (int i = 0; i < 6; i++) {
            Instant ts = BASIS.plus(Duration.ofMinutes(20L * i));
            repository.save(new SensorSample("innen", ts, 20 + i, 50 + i));
            repository.save(new SensorSample("aussen", ts, 10 + i, 60 + i));
        }

        long entfernt = repository.compactOlderThan(BASIS.plus(Duration.ofDays(1)));

        // Von je drei Punkten pro Stunde und Sensor bleibt einer: 12 -> 4, also 8 weg.
        assertEquals(8, entfernt);
        List<SensorSample> rest = repository.between(BASIS, BASIS.plus(Duration.ofDays(1)));
        assertEquals(4, rest.size());
        // Behalten wird der erste der Stunde - hier die Punkte um :00.
        rest.forEach(s -> assertEquals(0, s.timestamp().atZone(java.time.ZoneOffset.UTC).getMinute()));
    }

    @Test
    @TestTransaction
    void laesst_junge_messpunkte_unberuehrt() {
        for (int i = 0; i < 3; i++) {
            repository.save(new SensorSample("innen", BASIS.plus(Duration.ofMinutes(20L * i)), 21, 55));
        }

        assertEquals(0, repository.compactOlderThan(BASIS));
        assertEquals(3, repository.between(BASIS, BASIS.plus(Duration.ofHours(2))).size());
    }

    @Test
    @TestTransaction
    void ist_wiederholbar() {
        // Ein zweiter Lauf darf nichts mehr finden, sonst schrumpfte die Historie
        // bei jedem Durchgang weiter.
        for (int i = 0; i < 4; i++) {
            repository.save(new SensorSample("innen", BASIS.plus(Duration.ofMinutes(15L * i)), 21, 55));
        }
        Instant cutoff = BASIS.plus(Duration.ofDays(1));

        repository.compactOlderThan(cutoff);

        assertEquals(0, repository.compactOlderThan(cutoff));
    }
}
