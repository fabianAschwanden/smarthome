package fabianaschwanden.smarthome.domain.model.energy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Aggregiertes Ergebnis des Use Case: alle Quell-Messungen zu einem Zeitpunkt
 * plus optionaler Vergleich (vorhanden, sobald beide Quellen erfolgreich gemessen wurden).
 */
public record EnergySnapshot(
        Instant timestamp,
        List<PowerReading> readings,
        Optional<SourceComparison> comparison) {

    public EnergySnapshot {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp darf nicht null sein");
        }
        readings = List.copyOf(readings);
        if (comparison == null) {
            comparison = Optional.empty();
        }
    }
}
