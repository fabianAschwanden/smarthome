package fabianaschwanden.smarthome.domain.port.in.battery;

/**
 * Signalisiert den Versuch, das Relais manuell zu schalten, während die
 * Automatik aktiv ist (siehe SPEC §5: REST antwortet darauf mit 409).
 */
public class ManualSwitchNotAllowed extends RuntimeException {

    public ManualSwitchNotAllowed() {
        super("Relais kann nur im MANUAL-Modus manuell geschaltet werden");
    }
}
