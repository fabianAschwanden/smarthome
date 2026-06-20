package fabianaschwanden.smarthome.adapter.out.battery.smartfox;

import fabianaschwanden.smarthome.domain.model.battery.RelayState;

/** Der SMARTFOX-Schaltbefehl konnte nicht abgesetzt werden. */
public class RelaySwitchFailed extends RuntimeException {

    public RelaySwitchFailed(RelayState state, Throwable cause) {
        super("Relais konnte nicht auf " + state + " geschaltet werden: " + cause.getMessage(), cause);
    }
}
