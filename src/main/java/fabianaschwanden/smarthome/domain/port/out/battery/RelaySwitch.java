package fabianaschwanden.smarthome.domain.port.out.battery;

import fabianaschwanden.smarthome.domain.model.battery.RelayState;

/**
 * Getriebener Port: das physische Batterie-Relais (SMARTFOX-Relais 1).
 * Adapter in {@code adapter/out} implementieren diesen Port (HTTP bzw. Mock).
 */
public interface RelaySwitch {

    /** Schaltet das Relais auf den gewünschten Zustand. */
    void apply(RelayState state);
}
