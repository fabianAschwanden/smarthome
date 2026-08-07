package fabianaschwanden.smarthome.domain.port.in.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.AutoApplyState;

/**
 * Treiber-Port (Use Case): die Automatik, die die Ladeempfehlung ohne Nutzeraktion als
 * Zeitplan übernimmt – an- und abschaltbar, mit dem Ergebnis des letzten Laufs.
 */
public interface ManageAutoApply {

    AutoApplyState state();

    AutoApplyState setEnabled(boolean enabled);
}
