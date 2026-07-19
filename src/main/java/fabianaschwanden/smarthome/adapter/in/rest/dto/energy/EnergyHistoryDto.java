package fabianaschwanden.smarthome.adapter.in.rest.dto.energy;

import fabianaschwanden.smarthome.domain.model.energy.EnergyHistory;

import java.util.List;

/**
 * Transport-Objekt des Energie-Verlaufs: Bereich (day/week/month), kWh-Buckets und –
 * nur bei {@code day} – die Roh-Messpunkte für die Leistungskurve.
 */
public record EnergyHistoryDto(String range, List<EnergyBucketDto> buckets, List<EnergySampleDto> samples) {

    public static EnergyHistoryDto from(EnergyHistory history) {
        return new EnergyHistoryDto(
                history.range().name().toLowerCase(),
                history.buckets().stream().map(EnergyBucketDto::from).toList(),
                history.samples().stream().map(EnergySampleDto::from).toList());
    }
}
