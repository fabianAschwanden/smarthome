package fabianaschwanden.smarthome.adapter.in.rest.dto.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.AutoApplyState;

/**
 * Transport-Objekt der Lade-Automatik.
 *
 * <p>{@code lastOutcome} und {@code lastDetail} sagen, was beim letzten Lauf geschah –
 * gerade auch dann, wenn <em>nicht</em> geschaltet wurde. Ein stilles Nichts wäre im UI
 * nicht von «Automatik aus» zu unterscheiden.
 */
public record AutoApplyDto(boolean enabled, String lastRunDay, String lastOutcome, String lastDetail) {

    public static AutoApplyDto from(AutoApplyState state) {
        return new AutoApplyDto(
                state.enabled(),
                state.lastRunDay() == null ? null : state.lastRunDay().toString(),
                state.lastOutcome() == null ? null : state.lastOutcome().name(),
                state.lastDetail());
    }
}
