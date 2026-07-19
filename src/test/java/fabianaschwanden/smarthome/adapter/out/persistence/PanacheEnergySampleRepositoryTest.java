package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class PanacheEnergySampleRepositoryTest {

    private static final Instant CLEAR = Instant.parse("2100-01-01T00:00:00Z");

    @Inject
    PanacheEnergySampleRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteOlderThan(CLEAR); // Tabelle leeren (alles vor 2100).
    }

    @Test
    void speichertUndLiestImZeitfenster() {
        repository.save(new EnergySample(Instant.parse("2026-07-18T09:00:00Z"), 4000, 300));
        repository.save(new EnergySample(Instant.parse("2026-07-18T10:00:00Z"), 5000, 350));
        repository.save(new EnergySample(Instant.parse("2026-07-18T20:00:00Z"), 0, 900));

        List<EnergySample> window = repository.between(
                Instant.parse("2026-07-18T08:00:00Z"), Instant.parse("2026-07-18T12:00:00Z"));

        assertEquals(2, window.size());
        assertEquals(4000, window.get(0).pvWatt(), 1e-9); // aufsteigend nach Zeit
        assertEquals(5000, window.get(1).pvWatt(), 1e-9);
        assertEquals(3, repository.total());
    }

    @Test
    void loeschtAelterAlsCutoff() {
        repository.save(new EnergySample(Instant.parse("2026-06-01T00:00:00Z"), 100, 100));
        repository.save(new EnergySample(Instant.parse("2026-07-18T00:00:00Z"), 200, 200));

        long removed = repository.deleteOlderThan(Instant.parse("2026-07-01T00:00:00Z"));

        assertEquals(1, removed);
        assertEquals(1, repository.total());
    }
}
