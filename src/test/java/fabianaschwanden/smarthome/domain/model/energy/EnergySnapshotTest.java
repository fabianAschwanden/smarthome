package fabianaschwanden.smarthome.domain.model.energy;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class EnergySnapshotTest {

    private final Instant now = Instant.parse("2026-06-22T10:00:00Z");

    @Test
    void gueltigeInstanzBautKorrekt() {
        PowerReading r = PowerReading.of(PowerSource.FRONIUS, now, 0, 1000, null, 1000);
        EnergySnapshot s = new EnergySnapshot(now, List.of(r), Optional.empty());
        assertEquals(now, s.timestamp());
        assertEquals(1, s.readings().size());
        assertTrue(s.comparison().isEmpty());
    }

    @Test
    void timestampDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new EnergySnapshot(null, List.of(), Optional.empty()));
    }

    @Test
    void nullComparisonWirdEmpty() {
        EnergySnapshot s = new EnergySnapshot(now, List.of(), null);
        assertTrue(s.comparison().isEmpty());
    }

    @Test
    void readingsSindUnveraenderbar() {
        PowerReading r = PowerReading.error(PowerSource.SMARTFOX, now);
        EnergySnapshot s = new EnergySnapshot(now, List.of(r), Optional.empty());
        assertThrows(UnsupportedOperationException.class,
                () -> s.readings().add(r));
    }

    @Test
    void mitComparison() {
        SourceComparison c = new SourceComparison(
                PowerSource.FRONIUS, PowerSource.SMARTFOX, 50, 30, 20, 5.0);
        EnergySnapshot s = new EnergySnapshot(now, List.of(), Optional.of(c));
        assertTrue(s.comparison().isPresent());
        assertEquals(PowerSource.FRONIUS, s.comparison().get().first());
    }
}
