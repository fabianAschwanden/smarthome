package fabianaschwanden.smarthome.adapter.in.rest.dto.energy;

import fabianaschwanden.smarthome.domain.model.energy.EnergyBucket;

/**
 * Transport-Objekt eines Verlaufs-Abschnitts: Beginn (ISO-8601), Energie in kWh und
 * Eigennutzung (direkt verbrauchte PV-Produktion) in kWh.
 */
public record EnergyBucketDto(String start, double pvKwh, double consumptionKwh, double selfUseKwh) {

    public static EnergyBucketDto from(EnergyBucket b) {
        return new EnergyBucketDto(b.start().toString(), b.pvKwh(), b.consumptionKwh(), b.selfUseKwh());
    }
}
