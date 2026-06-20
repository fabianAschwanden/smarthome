package fabianaschwanden.smarthome.adapter.in.rest.dto.energy;

import fabianaschwanden.smarthome.domain.model.energy.EnergySnapshot;

import java.util.List;

/** Transport-Objekt des aggregierten Schnappschusses. */
public record EnergySnapshotDto(
        String timestamp,
        List<PowerReadingDto> readings,
        SourceComparisonDto comparison) {

    public static EnergySnapshotDto from(EnergySnapshot snapshot) {
        return new EnergySnapshotDto(
                snapshot.timestamp().toString(),
                snapshot.readings().stream().map(PowerReadingDto::from).toList(),
                snapshot.comparison().map(SourceComparisonDto::from).orElse(null));
    }
}
