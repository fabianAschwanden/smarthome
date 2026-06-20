package fabianaschwanden.smarthome.adapter.in.rest.dto.tuya;

import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import jakarta.validation.constraints.NotNull;

/**
 * Schalt-Anforderung. {@code confirm} bestätigt das Ausschalten eines kritischen
 * Schalters (z. B. Homecinema = WLAN); ohne Bestätigung antwortet der Server mit 409.
 */
public record SwitchCommandRequest(@NotNull SwitchState state, boolean confirm) {
}
