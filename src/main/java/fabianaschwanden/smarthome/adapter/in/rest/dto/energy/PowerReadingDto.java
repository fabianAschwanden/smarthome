package fabianaschwanden.smarthome.adapter.in.rest.dto.energy;

import fabianaschwanden.smarthome.domain.model.energy.PowerReading;

/** Transport-Objekt einer Quell-Messung (publizierte Sprache der REST-Schicht). */
public record PowerReadingDto(
        String source,
        String timestamp,
        double gridWatt,
        double pvWatt,
        Double batteryWatt,
        double consumptionWatt,
        String status,
        DailyEnergyDto daily) {

    /** Tages-/Relativwerte (Wh bzw. %); Felder null, wenn die Quelle sie nicht liefert. */
    public record DailyEnergyDto(
            Double productionWhToday,
            Double totalWh,
            Double autonomyPercent,
            Double selfConsumptionPercent) {
    }

    public static PowerReadingDto from(PowerReading r) {
        PowerReading.DailyEnergy d = r.daily();
        DailyEnergyDto daily = d == null ? null
                : new DailyEnergyDto(
                        d.productionWhToday(), d.totalWh(), d.autonomyPercent(), d.selfConsumptionPercent());
        return new PowerReadingDto(
                r.source().name(),
                r.timestamp().toString(),
                r.gridWatt(),
                r.pvWatt(),
                r.batteryWatt(),
                r.consumptionWatt(),
                r.status().name(),
                daily);
    }
}
