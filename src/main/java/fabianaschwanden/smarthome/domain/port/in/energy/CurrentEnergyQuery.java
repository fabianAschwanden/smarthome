package fabianaschwanden.smarthome.domain.port.in.energy;

import fabianaschwanden.smarthome.domain.model.energy.EnergySnapshot;

/**
 * Treiber-Port (Use Case): liefert den aktuellen Energie-Schnappschuss
 * über alle angebundenen Quellen inklusive Vergleich.
 */
public interface CurrentEnergyQuery {

    EnergySnapshot currentEnergy();
}
