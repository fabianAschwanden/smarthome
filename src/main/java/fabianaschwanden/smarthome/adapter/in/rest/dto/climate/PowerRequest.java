package fabianaschwanden.smarthome.adapter.in.rest.dto.climate;

import jakarta.validation.constraints.NotNull;

/** Ein/Aus-Anforderung. */
public record PowerRequest(@NotNull Boolean on) {
}
