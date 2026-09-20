package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.port.out.activation.DeviceActivationRepository;
import fabianaschwanden.smarthome.domain.model.activation.DeviceKind;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Driven Adapter — hält die stillgelegten Geräte. Nur die Ausnahme wird gespeichert:
 * Eine Zeile heisst «stillgelegt», keine Zeile heisst «in Betrieb».
 */
@ApplicationScoped
public class PanacheDeviceActivationRepository
        implements DeviceActivationRepository,
        PanacheRepositoryBase<DeviceDeactivationEntity, DeviceDeactivationEntity.Key> {

    @Override
    public Set<String> deactivated(DeviceKind kind) {
        return find("kind", kind.name()).stream()
                .map(e -> e.deviceId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional
    public void deactivate(DeviceKind kind, String deviceId, Instant at) {
        // Erst füllen, dann persistieren - siehe PanacheAutoApplyStateRepository.
        DeviceDeactivationEntity entity = findByIdOptional(new DeviceDeactivationEntity.Key(kind.name(), deviceId))
                .orElseGet(DeviceDeactivationEntity::new);
        entity.kind = kind.name();
        entity.deviceId = deviceId;
        entity.deactivatedAt = at;
        persist(entity);
    }

    @Override
    @Transactional
    public void reactivate(DeviceKind kind, String deviceId) {
        deleteById(new DeviceDeactivationEntity.Key(kind.name(), deviceId));
    }
}
