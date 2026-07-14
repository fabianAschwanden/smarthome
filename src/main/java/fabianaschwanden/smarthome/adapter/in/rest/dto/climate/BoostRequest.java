package fabianaschwanden.smarthome.adapter.in.rest.dto.climate;

import jakarta.validation.constraints.NotNull;

/** Boost-/Turbo-Anforderung. */
public record BoostRequest(@NotNull Boolean on) {
}
