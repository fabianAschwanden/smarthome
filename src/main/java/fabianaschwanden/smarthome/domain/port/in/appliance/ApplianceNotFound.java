package fabianaschwanden.smarthome.domain.port.in.appliance;

/** Es gibt keine Anlage mit der angefragten ID (REST: 404). */
public class ApplianceNotFound extends RuntimeException {

    public ApplianceNotFound(String id) {
        super("Keine Anlage mit ID '" + id + "'");
    }
}
