package fabianaschwanden.smarthome.domain.port.out.appliance;

/**
 * Die Anlage konnte nicht angesteuert werden (nicht erreichbar oder Schnittstelle
 * noch nicht angebunden). Treiber-Adapter bilden das auf 503 ab.
 */
public class ApplianceUnavailable extends RuntimeException {

    public ApplianceUnavailable(String message) {
        super(message);
    }
}
