package fabianaschwanden.smarthome.domain.service.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.AccuracyHistory;
import fabianaschwanden.smarthome.domain.model.forecast.AutoApplyOutcome;

import java.util.Optional;

/**
 * Der Schutzschalter vor der Automatik: Darf man der Prognose zutrauen, ohne Rückfrage
 * zu schalten?
 *
 * <p>Zwei Bedingungen, und die zweite ist die wichtigere: Die Prognose muss <b>genau
 * genug</b> gewesen sein, und es muss <b>genug ausgewertete Tage</b> geben, um das
 * überhaupt beurteilen zu können. Eine perfekte Trefferquote aus zwei Tagen ist keine
 * Trefferquote, sondern Zufall – und ohne diese Hürde würde die Automatik direkt nach
 * der Inbetriebnahme losschalten, wenn sie am wenigsten weiss.
 *
 * <p>Reine Funktion ohne Zustand.
 */
public class ForecastTrust {

    /**
     * Der Grund, aus dem <b>nicht</b> geschaltet werden darf – oder leer, wenn nichts
     * dagegen spricht.
     */
    public Optional<AutoApplyOutcome> objection(
            AccuracyHistory accuracy, int minRatedDays, double maxMapePercent) {

        if (accuracy == null || accuracy.ratedDays() < minRatedDays || accuracy.mapePercent().isEmpty()) {
            return Optional.of(AutoApplyOutcome.NOT_ENOUGH_DATA);
        }
        if (accuracy.mapePercent().getAsDouble() > maxMapePercent) {
            return Optional.of(AutoApplyOutcome.FORECAST_UNRELIABLE);
        }
        return Optional.empty();
    }
}
