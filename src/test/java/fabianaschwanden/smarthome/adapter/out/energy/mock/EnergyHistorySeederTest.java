package fabianaschwanden.smarthome.adapter.out.energy.mock;

import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySampleRepository;
import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyHistorySeederTest {

    private final CountingRepo repo = new CountingRepo();

    @Test
    void seedetHistorieWennAktiviertUndMockUndLeer() {
        new EnergyHistorySeeder(repo, true, false, "Europe/Zurich").onStart(new StartupEvent());

        // ~45 Tage à 24 Stunden -> viele Messpunkte, PV tagsüber > 0.
        assertTrue(repo.saved.size() > 1000, "erwartet viele Messpunkte, war " + repo.saved.size());
        assertTrue(repo.saved.stream().anyMatch(s -> s.pvWatt() > 0));
    }

    @Test
    void seedetNichtImRealbetrieb() {
        new EnergyHistorySeeder(repo, true, true, "Europe/Zurich").onStart(new StartupEvent());
        assertEquals(0, repo.saved.size());
    }

    @Test
    void seedetNichtWennDeaktiviert() {
        new EnergyHistorySeeder(repo, false, false, "Europe/Zurich").onStart(new StartupEvent());
        assertEquals(0, repo.saved.size());
    }

    @Test
    void seedetNichtWennTabelleBereitsGefuellt() {
        repo.saved.add(new EnergySample(Instant.now(), 100, 100));
        new EnergyHistorySeeder(repo, true, false, "Europe/Zurich").onStart(new StartupEvent());
        assertEquals(1, repo.saved.size());
    }

    private static final class CountingRepo implements EnergySampleRepository {
        private final List<EnergySample> saved = new ArrayList<>();

        @Override public void save(EnergySample sample) { saved.add(sample); }
        @Override public List<EnergySample> between(Instant from, Instant to) { return List.of(); }
        @Override public long deleteOlderThan(Instant cutoff) { return 0; }
        @Override public long total() { return saved.size(); }
    }
}
