package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.model.alert.AlertSettings;
import fabianaschwanden.smarthome.domain.port.out.alert.AlertSettingsRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Optional;

/** Driven Adapter — speichert die (einzige) Alert-Einstellungszeile als JPA-Entity. */
@ApplicationScoped
public class PanacheAlertSettingsRepository
        implements AlertSettingsRepository, PanacheRepositoryBase<AlertSettingsEntity, String> {

    @Override
    public Optional<AlertSettings> load() {
        return findByIdOptional(AlertSettingsEntity.SINGLETON_ID)
                .map(e -> new AlertSettings(e.enabled, e.ntfyTopic));
    }

    @Override
    @Transactional
    public void save(AlertSettings settings) {
        AlertSettingsEntity entity =
                findByIdOptional(AlertSettingsEntity.SINGLETON_ID).orElseGet(AlertSettingsEntity::new);
        entity.id = AlertSettingsEntity.SINGLETON_ID;
        entity.enabled = settings.enabled();
        entity.ntfyTopic = settings.ntfyTopic();
        persist(entity);
    }
}
