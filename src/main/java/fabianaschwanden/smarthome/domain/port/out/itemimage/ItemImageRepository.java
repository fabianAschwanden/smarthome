package fabianaschwanden.smarthome.domain.port.out.itemimage;

import fabianaschwanden.smarthome.domain.model.itemimage.ItemImage;

import java.util.Optional;

/**
 * Getriebener Port: Persistenz der Item-Bilder. Implementiert im Persistence-Adapter;
 * nimmt/liefert Domänen-Modelle, nie JPA-Entities.
 */
public interface ItemImageRepository {

    ItemImage save(ItemImage image);

    Optional<ItemImage> byItemId(String itemId);

    void delete(String itemId);
}
