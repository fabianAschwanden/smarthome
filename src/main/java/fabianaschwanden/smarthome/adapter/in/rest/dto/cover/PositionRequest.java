package fabianaschwanden.smarthome.adapter.in.rest.dto.cover;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Positions-Anforderung (0..100). */
public record PositionRequest(@Min(0) @Max(100) int position) {
}
