package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.model.battery.SunGuard;
import fabianaschwanden.smarthome.domain.port.out.battery.SunGuardRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Driven Adapter — übersetzt zwischen dem Domänen-Record {@code SunGuard} und der
 * JPA-Entity. Fehlt die Zeile, gilt der Standard: Wächter aus.
 */
@ApplicationScoped
public class PanacheSunGuardRepository
        implements SunGuardRepository, PanacheRepositoryBase<SunGuardEntity, String> {

    @Override
    public SunGuard load() {
        SunGuardEntity entity = findById(SunGuardEntity.SINGLETON_ID);
        if (entity == null) {
            return SunGuard.disabled();
        }
        return new SunGuard(entity.enabled, entity.armed, entity.lastTrippedAt);
    }

    @Override
    @Transactional
    public void save(SunGuard guard) {
        // Erst füllen, dann persistieren - siehe PanacheAutoApplyStateRepository.
        SunGuardEntity entity = findByIdOptional(SunGuardEntity.SINGLETON_ID)
                .orElseGet(SunGuardEntity::new);
        entity.id = SunGuardEntity.SINGLETON_ID;
        entity.enabled = guard.enabled();
        entity.armed = guard.armed();
        entity.lastTrippedAt = guard.lastTrippedAt();
        persist(entity);
    }
}
