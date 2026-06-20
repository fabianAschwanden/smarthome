package fabianaschwanden.smarthome.domain.port.in.itemimage;

/** Für das angefragte Item ist kein Bild hinterlegt (REST: 404). */
public class ItemImageNotFound extends RuntimeException {

    public ItemImageNotFound(String itemId) {
        super("Kein Bild für Item '" + itemId + "'");
    }
}
