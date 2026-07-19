package fabianaschwanden.smarthome.domain.port.in.energy;

import fabianaschwanden.smarthome.domain.model.energy.EnergyHistory;
import fabianaschwanden.smarthome.domain.model.energy.HistoryRange;

/**
 * Treiber-Port (Use Case): der Energie-Verlauf (Verbrauch und PV-Produktion) als
 * kWh-Zeitreihe für einen {@link HistoryRange}.
 */
public interface EnergyHistoryQuery {

    EnergyHistory history(HistoryRange range);
}
