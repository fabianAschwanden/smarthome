package fabianaschwanden.smarthome.domain.port.in.itemimage;

import fabianaschwanden.smarthome.domain.model.itemimage.ItemImage;

import java.util.Optional;

/**
 * Treiber-Port: Bilder für Items hinterlegen, lesen und entfernen. Die {@code itemId}
 * ist die fachliche Geräte-ID (z. B. {@code stehlampe}) – typ-übergreifend.
 */
public interface ManageItemImages {

    /** Legt das Bild an bzw. überschreibt es. */
    ItemImage put(String itemId, String dataUrl);

    Optional<ItemImage> byItemId(String itemId);

    void delete(String itemId);
}
