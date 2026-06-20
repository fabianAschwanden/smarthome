package fabianaschwanden.smarthome.adapter.in.rest.dto.battery;

import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import jakarta.validation.constraints.NotNull;

/** Manuelle Schalt-Anforderung. */
public record SwitchRelayRequest(@NotNull RelayState state) {
}
