package fabianaschwanden.smarthome.application.service.energy;

import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
import fabianaschwanden.smarthome.domain.model.energy.EnergySnapshot;
import fabianaschwanden.smarthome.domain.model.energy.PowerReading;
import fabianaschwanden.smarthome.domain.model.energy.PowerSource;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySampleRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergySamplerTest {

    private final Instant now = Instant.parse("2026-07-18T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final CapturingRepo repo = new CapturingRepo();

    @Test
    void speichertMomentanleistungDerFroniusMessung() {
        PowerReading fronius = PowerReading.of(PowerSource.FRONIUS, now, -500, 4200.0, null, 700.0);
        EnergySampler sampler = new EnergySampler(() -> snapshot(fronius), repo, clock, 45);

        sampler.sample();

        assertEquals(1, repo.saved.size());
        assertEquals(4200.0, repo.saved.get(0).pvWatt(), 1e-9);
        assertEquals(700.0, repo.saved.get(0).consumptionWatt(), 1e-9);
        assertEquals(now, repo.saved.get(0).timestamp());
    }

    @Test
    void bevorzugtFroniusVorSmartfox() {
        PowerReading smartfox = PowerReading.of(PowerSource.SMARTFOX, now, 0, 1000.0, null, 200.0);
        PowerReading fronius = PowerReading.of(PowerSource.FRONIUS, now, 0, 4200.0, null, 700.0);
        EnergySampler sampler = new EnergySampler(() -> snapshot(smartfox, fronius), repo, clock, 45);

        sampler.sample();

        assertEquals(4200.0, repo.saved.get(0).pvWatt(), 1e-9);
    }

    @Test
    void speichertNichtsWennKeineOkMessung() {
        PowerReading err = PowerReading.error(PowerSource.FRONIUS, now);
        EnergySampler sampler = new EnergySampler(() -> snapshot(err), repo, clock, 45);

        sampler.sample();

        assertTrue(repo.saved.isEmpty());
    }

    @Test
    void cleanupLoeschtAelterAlsAufbewahrung() {
        EnergySampler sampler = new EnergySampler(() -> snapshot(), repo, clock, 45);

        sampler.cleanup();

        assertEquals(now.minus(Duration.ofDays(45)), repo.cutoff);
    }

    private EnergySnapshot snapshot(PowerReading... readings) {
        return new EnergySnapshot(now, List.of(readings), Optional.empty());
    }

    private static final class CapturingRepo implements EnergySampleRepository {
        private final List<EnergySample> saved = new ArrayList<>();
        private Instant cutoff;

        @Override public void save(EnergySample sample) { saved.add(sample); }
        @Override public List<EnergySample> between(Instant from, Instant to) { return List.of(); }
        @Override public long deleteOlderThan(Instant c) { this.cutoff = c; return 0; }
        @Override public long total() { return saved.size(); }
    }
}
