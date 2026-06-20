package fabianaschwanden.smarthome.adapter.in.rest.dto.climate;

import fabianaschwanden.smarthome.domain.model.climate.Climate;

/** Transport-Objekt einer Klimaanlage. {@code currentTemp} = -1, wenn unbekannt. */
public record ClimateDto(
        String id,
        String name,
        String room,
        boolean power,
        String mode,
        int targetTemp,
        int currentTemp,
        boolean online,
        String observedAt) {

    public static ClimateDto from(Climate c) {
        return new ClimateDto(c.id(), c.name(), c.room(), c.power(), c.mode().name(),
                c.targetTemp(), c.currentTemp(), c.online(), c.observedAt().toString());
    }
}
