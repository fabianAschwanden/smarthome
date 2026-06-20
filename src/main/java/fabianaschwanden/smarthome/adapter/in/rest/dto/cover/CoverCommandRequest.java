package fabianaschwanden.smarthome.adapter.in.rest.dto.cover;

import fabianaschwanden.smarthome.domain.model.cover.CoverCommand;
import jakarta.validation.constraints.NotNull;

/** Grundbefehl-Anforderung (Auf/Ab/Stopp). */
public record CoverCommandRequest(@NotNull CoverCommand command) {
}
