package fabianaschwanden.smarthome.adapter.in.rest.dto.energy;

import fabianaschwanden.smarthome.domain.model.energy.EnergySample;

/** Transport-Objekt eines Roh-Messpunkts (Leistungskurve der Tagesansicht). */
public record EnergySampleDto(String timestamp, double pvWatt, double consumptionWatt) {

    public static EnergySampleDto from(EnergySample s) {
        return new EnergySampleDto(s.timestamp().toString(), s.pvWatt(), s.consumptionWatt());
    }
}
