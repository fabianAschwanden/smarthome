package fabianaschwanden.smarthome.domain.model.energy;

/**
 * Differenz zwischen zwei Quellen (SPEC §5). Positiver Delta = erste Quelle
 * meldet mehr als die zweite. {@code consumptionDeltaPercent} ist relativ zum
 * Betrag des grösseren der beiden Verbrauchswerte.
 */
public record SourceComparison(
        PowerSource first,
        PowerSource second,
        double consumptionDeltaWatt,
        double pvDeltaWatt,
        double gridDeltaWatt,
        double consumptionDeltaPercent) {
}
