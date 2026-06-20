package fabianaschwanden.smarthome.adapter.in.rest.dto.climate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Soll-Temperatur-Anforderung (°C, gültiger Bereich siehe Climate). */
public record TargetTempRequest(@Min(16) @Max(30) int temperature) {
}
