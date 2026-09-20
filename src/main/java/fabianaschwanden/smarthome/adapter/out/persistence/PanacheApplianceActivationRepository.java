package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceActivationRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Driven Adapter — hält die stillgelegten Anlagen. Nur die Ausnahme wird gespeichert:
 * Eine Zeile heisst «deaktiviert», keine Zeile heisst «in Betrieb».
 */
@ApplicationScoped
public class PanacheApplianceActivationRepository
        implements ApplianceActivationRepository, PanacheRepositoryBase<ApplianceDeactivationEntity, String> {

    @Override
    public Set<String> deactivated() {
        return streamAll().map(e -> e.applianceId).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional
    public void deactivate(String applianceId, Instant at) {
        // Erst füllen, dann persistieren - siehe PanacheAutoApplyStateRepository.
        ApplianceDeactivationEntity entity = findByIdOptional(applianceId)
                .orElseGet(ApplianceDeactivationEntity::new);
        entity.applianceId = applianceId;
        entity.deactivatedAt = at;
        persist(entity);
    }

    @Override
    @Transactional
    public void reactivate(String applianceId) {
        deleteById(applianceId);
    }
}
