package fabianaschwanden.smarthome.domain.port.in.battery;

import fabianaschwanden.smarthome.domain.model.battery.BatteryControl;
import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;

/**
 * Treiber-Port (Use Case): Batteriesteuerung über das SMARTFOX-Relais.
 * Modus setzen, im Manuell-Modus schalten und den aktuellen Stand abfragen.
 */
public interface ControlBattery {

    /** Aktueller Steuerstand (Modus, gewünschter Relais-Zustand, Zeit). */
    BatteryControl status();

    /** Modus wechseln; liefert den neuen Stand. */
    BatteryControl changeMode(ControlMode mode);

    /**
     * Relais manuell schalten. Nur im {@code MANUAL}-Modus erlaubt.
     *
     * @throws ManualSwitchNotAllowed wenn der Modus {@code AUTO} ist – dann besitzt
     *                                die Automatik den Relais-Zustand.
     */
    BatteryControl switchRelay(RelayState state);
}
