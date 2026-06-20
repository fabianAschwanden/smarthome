package fabianaschwanden.smarthome.domain.port.in.tuya;

/** Es gibt keinen Schalter mit der angefragten ID (REST: 404). */
public class SwitchNotFound extends RuntimeException {

    public SwitchNotFound(String id) {
        super("Kein Schalter mit ID '" + id + "'");
    }
}
