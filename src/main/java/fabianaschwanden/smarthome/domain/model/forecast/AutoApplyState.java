package fabianaschwanden.smarthome.domain.model.forecast;

import java.time.LocalDate;

/**
 * Die Automatik und was sie zuletzt getan hat.
 *
 * <p>{@code lastRunDay} ist null, solange noch nie gelaufen. Der Tag wird auch dann
 * festgehalten, wenn nichts übernommen wurde – sonst liefe die Automatik nach einem
 * Neustart am selben Tag erneut und legte einen zweiten Zeitplan an.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record AutoApplyState(
        boolean enabled, LocalDate lastRunDay, AutoApplyOutcome lastOutcome, String lastDetail) {

    public AutoApplyState {
        lastDetail = lastDetail == null ? "" : lastDetail;
    }

    /** Standard: aus. Automatisches Schalten ist eine bewusste Entscheidung. */
    public static AutoApplyState disabled() {
        return new AutoApplyState(false, null, null, "");
    }

    public AutoApplyState withEnabled(boolean newEnabled) {
        return new AutoApplyState(newEnabled, lastRunDay, lastOutcome, lastDetail);
    }

    public AutoApplyState ranOn(LocalDate day, AutoApplyOutcome outcome, String detail) {
        return new AutoApplyState(enabled, day, outcome, detail);
    }

    /** Ist die Automatik heute schon gelaufen? */
    public boolean ranOn(LocalDate day) {
        return lastRunDay != null && lastRunDay.equals(day);
    }
}
