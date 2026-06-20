package fabianaschwanden.smarthome.application.service.itemimage;

import fabianaschwanden.smarthome.domain.model.itemimage.ItemImage;
import fabianaschwanden.smarthome.domain.port.in.itemimage.ManageItemImages;
import fabianaschwanden.smarthome.domain.port.out.itemimage.ItemImageRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.Optional;

/**
 * Application-Service: orchestriert das Hinterlegen, Lesen und Entfernen von
 * Item-Bildern. Validierung steckt in der Domäne ({@link ItemImage}); hier nur die
 * Orchestrierung über den Repository-Port.
 */
@ApplicationScoped
public class ItemImageService implements ManageItemImages {

    private final ItemImageRepository repository;
    private final Clock clock;

    @Inject
    public ItemImageService(ItemImageRepository repository) {
        this(repository, Clock.systemUTC());
    }

    // Sichtbar fürs Testen.
    ItemImageService(ItemImageRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public ItemImage put(String itemId, String dataUrl) {
        return repository.save(new ItemImage(itemId, dataUrl, clock.instant()));
    }

    @Override
    public Optional<ItemImage> byItemId(String itemId) {
        return repository.byItemId(itemId);
    }

    @Override
    public void delete(String itemId) {
        repository.delete(itemId);
    }
}
