package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.model.applianceschedule.ApplianceSchedule;
import fabianaschwanden.smarthome.domain.port.out.applianceschedule.ApplianceScheduleRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Driven Adapter — übersetzt zwischen dem Domänen-Record {@code ApplianceSchedule} und
 * der JPA-Entity.
 */
@ApplicationScoped
public class PanacheApplianceScheduleRepository
        implements ApplianceScheduleRepository, PanacheRepositoryBase<ApplianceScheduleEntity, UUID> {

    @Override
    @Transactional
    public ApplianceSchedule save(ApplianceSchedule schedule) {
        // Erst füllen, dann persistieren - siehe PanacheForecastAccuracyRepository.
        ApplianceScheduleEntity entity =
                findByIdOptional(schedule.id()).orElseGet(ApplianceScheduleEntity::new);
        entity.id = schedule.id();
        entity.applianceId = schedule.applianceId();
        entity.function = schedule.function().name();
        entity.state = schedule.state().name();
        entity.fireAt = schedule.fireAt();
        entity.enabled = schedule.enabled();
        persist(entity);
        return schedule;
    }

    @Override
    public List<ApplianceSchedule> all() {
        return findAll(Sort.by("fireAt")).list().stream().map(PanacheApplianceScheduleRepository::toDomain).toList();
    }

    @Override
    public List<ApplianceSchedule> allEnabled() {
        return list("enabled", true).stream().map(PanacheApplianceScheduleRepository::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        deleteById(id);
    }

    private static ApplianceSchedule toDomain(ApplianceScheduleEntity entity) {
        return new ApplianceSchedule(
                entity.id,
                entity.applianceId,
                ApplianceFunction.valueOf(entity.function),
                FunctionState.valueOf(entity.state),
                entity.fireAt,
                entity.enabled);
    }
}
