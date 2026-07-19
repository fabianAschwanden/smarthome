package fabianaschwanden.smarthome.domain.port.out.itemimage;

import fabianaschwanden.smarthome.domain.model.itemimage.ItemImage;

import java.util.List;
import java.util.Optional;

/**
 * Getriebener Port: Persistenz der Item-Bilder. Implementiert im Persistence-Adapter;
 * nimmt/liefert Domänen-Modelle, nie JPA-Entities.
 */
public interface ItemImageRepository {

    ItemImage save(ItemImage image);

    Optional<ItemImage> byItemId(String itemId);

    /** Alle Bilder – für Export/Backup. */
    List<ItemImage> all();

    void delete(String itemId);
}
