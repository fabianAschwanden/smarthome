package fabianaschwanden.smarthome.domain.port.out.cover;

/**
 * Die Store konnte nicht angesteuert werden (nicht erreichbar oder Adapter nicht
 * betriebsbereit). Treiber-Adapter bilden das auf 503 ab.
 */
public class CoverUnavailable extends RuntimeException {

    public CoverUnavailable(String message) {
        super(message);
    }
}
