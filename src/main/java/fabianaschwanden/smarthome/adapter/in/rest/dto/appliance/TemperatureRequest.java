package fabianaschwanden.smarthome.adapter.in.rest.dto.appliance;

/** Soll-Temperatur-Anforderung (°C). Bereichsprüfung erfolgt in der Domäne. */
public record TemperatureRequest(int target) {
}
