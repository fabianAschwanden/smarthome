package fabianaschwanden.smarthome.domain.port.in.climate;

/** Es gibt keine Klimaanlage mit der angefragten ID (REST: 404). */
public class ClimateNotFound extends RuntimeException {

    public ClimateNotFound(String id) {
        super("Keine Klimaanlage mit ID '" + id + "'");
    }
}
