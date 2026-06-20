package fabianaschwanden.smarthome.adapter.in.rest.dto.climate;

import fabianaschwanden.smarthome.domain.model.climate.ClimateMode;
import jakarta.validation.constraints.NotNull;

/** Modus-Anforderung. */
public record ModeRequest(@NotNull ClimateMode mode) {
}
