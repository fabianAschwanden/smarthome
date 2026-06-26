package fabianaschwanden.smarthome.domain.port.out.battery;

import fabianaschwanden.smarthome.domain.model.battery.RelayState;

import java.util.Optional;

/**
 * Getriebener Port: das physische Batterie-Relais (SMARTFOX-Relais 1).
 * Adapter in {@code adapter/out} implementieren diesen Port (HTTP bzw. Mock).
 */
public interface RelaySwitch {

    /** Schaltet das Relais auf den gewünschten Zustand. */
    void apply(RelayState state);

    /**
     * Liest den aktuellen Ist-Zustand des Relais vom Gerät.
     *
     * @return der gelesene Zustand, oder {@code empty}, wenn nicht ermittelbar
     *         (Gerät nicht erreichbar, Feld fehlt). Default {@code empty}.
     */
    default Optional<RelayState> read() {
        return Optional.empty();
    }
}
