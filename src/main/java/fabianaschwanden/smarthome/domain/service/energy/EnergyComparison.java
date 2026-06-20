package fabianaschwanden.smarthome.domain.service.energy;

import fabianaschwanden.smarthome.domain.model.energy.EnergySnapshot;
import fabianaschwanden.smarthome.domain.model.energy.PowerReading;
import fabianaschwanden.smarthome.domain.model.energy.SourceComparison;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Reiner Domain-Service (zustandslos, framework-frei): baut aus den Messungen
 * der Quellen einen {@link EnergySnapshot} und berechnet den Vergleich, sobald
 * mindestens zwei erfolgreiche Messungen vorliegen.
 */
public final class EnergyComparison {

    public EnergySnapshot toSnapshot(Instant timestamp, List<PowerReading> readings) {
        return new EnergySnapshot(timestamp, readings, compareFirstTwoOk(readings));
    }

    private Optional<SourceComparison> compareFirstTwoOk(List<PowerReading> readings) {
        List<PowerReading> ok = readings.stream().filter(PowerReading::isOk).toList();
        if (ok.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(compare(ok.get(0), ok.get(1)));
    }

    public SourceComparison compare(PowerReading a, PowerReading b) {
        double consumptionDelta = a.consumptionWatt() - b.consumptionWatt();
        double pvDelta = a.pvWatt() - b.pvWatt();
        double gridDelta = a.gridWatt() - b.gridWatt();
        double reference = Math.max(Math.abs(a.consumptionWatt()), Math.abs(b.consumptionWatt()));
        double percent = reference == 0 ? 0 : (consumptionDelta / reference) * 100.0;
        return new SourceComparison(a.source(), b.source(), consumptionDelta, pvDelta, gridDelta, percent);
    }
}
