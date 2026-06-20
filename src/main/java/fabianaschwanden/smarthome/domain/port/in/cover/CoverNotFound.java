package fabianaschwanden.smarthome.domain.port.in.cover;

/** Es gibt keine Store mit der angefragten ID (REST: 404). */
public class CoverNotFound extends RuntimeException {

    public CoverNotFound(String id) {
        super("Keine Store mit ID '" + id + "'");
    }
}
