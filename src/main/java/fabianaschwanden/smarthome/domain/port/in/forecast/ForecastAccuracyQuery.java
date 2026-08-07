package fabianaschwanden.smarthome.domain.port.in.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.AccuracyHistory;

/**
 * Treiber-Port (Use Case): Wie gut lag die Prognose in den letzten Tagen?
 *
 * <p>Die Zahl ist die Grundlage für alles Automatische: Wer die Anlage nach der
 * Prognose steuern lässt, muss wissen, wie sehr er ihr trauen darf.
 */
public interface ForecastAccuracyQuery {

    /** Die letzten {@code days} Tage samt MAPE. */
    AccuracyHistory accuracy(int days);
}
