package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.model.itemimage.ItemImage;
import fabianaschwanden.smarthome.domain.port.out.itemimage.ItemImageRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Optional;

/** Driven Adapter — übersetzt zwischen Domänen-Modell und JPA-Entity. */
@ApplicationScoped
public class PanacheItemImageRepository
        implements ItemImageRepository, PanacheRepositoryBase<ItemImageEntity, String> {

    @Override
    @Transactional
    public ItemImage save(ItemImage image) {
        ItemImageEntity entity = findByIdOptional(image.itemId()).orElseGet(ItemImageEntity::new);
        entity.itemId = image.itemId();
        entity.dataUrl = image.dataUrl();
        entity.updatedAt = image.updatedAt();
        persist(entity);
        return image;
    }

    @Override
    public Optional<ItemImage> byItemId(String itemId) {
        return findByIdOptional(itemId).map(this::toDomain);
    }

    @Override
    @Transactional
    public void delete(String itemId) {
        deleteById(itemId);
    }

    private ItemImage toDomain(ItemImageEntity e) {
        return new ItemImage(e.itemId, e.dataUrl, e.updatedAt);
    }
}
