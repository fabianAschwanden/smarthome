package fabianaschwanden.smarthome.domain.port.out.battery;

import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayReading;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;

import java.util.Optional;

/**
 * Getriebener Port: das physische Batterie-Relais (SMARTFOX-Relais 1). Das Relais ist
 * dreiwertig (Aus / Manuell / Automatik); der Domänen-Steuerstand bildet das als
 * ({@link ControlMode}, {@link RelayState}) ab. Adapter in {@code adapter/out}
 * implementieren diesen Port (HTTP bzw. Mock).
 */
public interface RelaySwitch {

    /**
     * Stellt das Relais auf den Ziel-Modus. {@code AUTO} übergibt die Kontrolle an die
     * geräteeigene Überschuss-Automatik; im {@code MANUAL}-Modus setzt {@code state}
     * Ein/Aus. Bei {@code AUTO} wird {@code state} ignoriert.
     */
    void apply(ControlMode mode, RelayState state);

    /**
     * Liest Modus und Ist-Zustand des Relais vom Gerät.
     *
     * @return die Ablesung, oder {@code empty}, wenn nicht ermittelbar (Gerät nicht
     *         erreichbar, Feld fehlt). Default {@code empty}.
     */
    default Optional<RelayReading> read() {
        return Optional.empty();
    }
}
