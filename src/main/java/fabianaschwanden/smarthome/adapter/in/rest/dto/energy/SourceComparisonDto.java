package fabianaschwanden.smarthome.adapter.in.rest.dto.energy;

import fabianaschwanden.smarthome.domain.model.energy.SourceComparison;

/** Transport-Objekt des Quellen-Vergleichs. */
public record SourceComparisonDto(
        String first,
        String second,
        double consumptionDeltaWatt,
        double pvDeltaWatt,
        double gridDeltaWatt,
        double consumptionDeltaPercent) {

    public static SourceComparisonDto from(SourceComparison c) {
        return new SourceComparisonDto(
                c.first().name(),
                c.second().name(),
                c.consumptionDeltaWatt(),
                c.pvDeltaWatt(),
                c.gridDeltaWatt(),
                c.consumptionDeltaPercent());
    }
}
