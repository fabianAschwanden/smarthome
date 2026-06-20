package fabianaschwanden.smarthome.domain.port.in.tuya;

import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import fabianaschwanden.smarthome.domain.model.tuya.TuyaSwitch;

import java.util.List;

/**
 * Treiber-Port (Use Case): mehrere Tuya-Schalter verwalten – alle auflisten,
 * einen gezielt schalten.
 */
public interface ControlSwitches {

    /** Alle konfigurierten Schalter mit ihrem aktuellen Zustand. */
    List<TuyaSwitch> list();

    /**
     * Schaltet den Schalter mit der ID auf den gewünschten Zustand.
     *
     * @param confirmed Bestätigung für kritische Schalter beim Ausschalten.
     * @throws SwitchNotFound               wenn keine ID passt.
     * @throws CriticalConfirmationRequired wenn ein kritischer Schalter ohne
     *                                      Bestätigung ausgeschaltet werden soll.
     */
    TuyaSwitch switchTo(String id, SwitchState state, boolean confirmed);
}
