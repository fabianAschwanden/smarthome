package fabianaschwanden.smarthome.domain.model.energy;

import java.util.List;

/**
 * Aggregierte Energie-Historie eines {@link HistoryRange}: eine lückenlose, zeitlich
 * aufsteigende Liste von {@link EnergyBucket}s (leere Abschnitte sind mit 0 kWh
 * enthalten, damit die Anzeige eine durchgehende Achse hat). Für {@code DAY} sind
 * zusätzlich die Roh-Messpunkte enthalten – die Anzeige zeichnet daraus die
 * Leistungskurve (kW über den Tag); für Woche/Monat bleibt die Liste leer.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record EnergyHistory(HistoryRange range, List<EnergyBucket> buckets, List<EnergySample> samples) {

    public EnergyHistory {
        if (range == null) {
            throw new IllegalArgumentException("range darf nicht null sein");
        }
        buckets = buckets == null ? List.of() : List.copyOf(buckets);
        samples = samples == null ? List.of() : List.copyOf(samples);
    }
}
