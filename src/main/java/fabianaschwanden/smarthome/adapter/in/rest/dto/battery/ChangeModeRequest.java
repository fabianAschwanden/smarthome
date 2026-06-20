package fabianaschwanden.smarthome.adapter.in.rest.dto.battery;

import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import jakarta.validation.constraints.NotNull;

/** Modus-Wechsel-Anforderung. */
public record ChangeModeRequest(@NotNull ControlMode mode) {
}
