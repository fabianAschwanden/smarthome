package fabianaschwanden.smarthome.domain.service.energy;

import fabianaschwanden.smarthome.domain.model.energy.EnergySnapshot;
import fabianaschwanden.smarthome.domain.model.energy.PowerReading;
import fabianaschwanden.smarthome.domain.model.energy.PowerSource;
import fabianaschwanden.smarthome.domain.model.energy.SourceComparison;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyComparisonTest {

    private final EnergyComparison comparison = new EnergyComparison();
    private final Instant now = Instant.parse("2026-06-19T12:00:00Z");

    @Test
    void berechnetDeltaZwischenZweiQuellen() {
        PowerReading fronius = PowerReading.of(PowerSource.FRONIUS, now, -4000, 5000, null, 1000);
        PowerReading smartfox = PowerReading.of(PowerSource.SMARTFOX, now, -3900, 4900, null, 1000);

        SourceComparison result = comparison.compare(fronius, smartfox);

        assertEquals(0.0, result.consumptionDeltaWatt(), 0.001);
        assertEquals(100.0, result.pvDeltaWatt(), 0.001);
        assertEquals(-100.0, result.gridDeltaWatt(), 0.001);
    }

    @Test
    void snapshotHatVergleichBeiZweiOkMessungen() {
        List<PowerReading> readings = List.of(
                PowerReading.of(PowerSource.FRONIUS, now, -4000, 5000, null, 1000),
                PowerReading.of(PowerSource.SMARTFOX, now, -3900, 4900, null, 1100));

        EnergySnapshot snapshot = comparison.toSnapshot(now, readings);

        assertTrue(snapshot.comparison().isPresent());
        assertEquals(-100.0, snapshot.comparison().get().consumptionDeltaWatt(), 0.001);
    }

    @Test
    void snapshotOhneVergleichWennEineQuelleFehlerhaft() {
        List<PowerReading> readings = List.of(
                PowerReading.of(PowerSource.FRONIUS, now, -4000, 5000, null, 1000),
                PowerReading.error(PowerSource.SMARTFOX, now));

        EnergySnapshot snapshot = comparison.toSnapshot(now, readings);

        assertTrue(snapshot.comparison().isEmpty());
        assertEquals(2, snapshot.readings().size());
    }
}
