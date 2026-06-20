package fabianaschwanden.smarthome.domain.port.out.climate;

/**
 * Die Klimaanlage konnte nicht angesteuert werden (nicht erreichbar oder
 * Schnittstelle noch nicht angebunden). Treiber-Adapter bilden das auf 503 ab.
 */
public class ClimateUnavailable extends RuntimeException {

    public ClimateUnavailable(String message) {
        super(message);
    }
}
