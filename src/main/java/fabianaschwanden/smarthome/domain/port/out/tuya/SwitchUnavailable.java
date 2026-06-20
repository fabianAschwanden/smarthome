package fabianaschwanden.smarthome.domain.port.out.tuya;

/**
 * Das Schaltgerät konnte nicht angesteuert werden (nicht erreichbar oder Adapter
 * noch nicht betriebsbereit). Treiber-Adapter bilden das auf einen passenden
 * Fehlerstatus ab (REST: 503).
 */
public class SwitchUnavailable extends RuntimeException {

    public SwitchUnavailable(String message) {
        super(message);
    }
}
