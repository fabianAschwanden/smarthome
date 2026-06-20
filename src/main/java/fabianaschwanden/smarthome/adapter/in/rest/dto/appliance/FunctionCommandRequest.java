package fabianaschwanden.smarthome.adapter.in.rest.dto.appliance;

import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import jakarta.validation.constraints.NotNull;

/** Schalt-Anforderung für eine Anlagen-Funktion. */
public record FunctionCommandRequest(@NotNull FunctionState state) {
}
