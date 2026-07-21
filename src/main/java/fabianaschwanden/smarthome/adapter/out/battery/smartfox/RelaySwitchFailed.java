package fabianaschwanden.smarthome.adapter.out.battery.smartfox;

import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;

/** Der SMARTFOX-Schaltbefehl konnte nicht abgesetzt werden. */
public class RelaySwitchFailed extends RuntimeException {

    public RelaySwitchFailed(ControlMode mode, RelayState state, Throwable cause) {
        super("Relais konnte nicht auf Modus " + mode + " / " + state
                + " gestellt werden: " + cause.getMessage(), cause);
    }
}
