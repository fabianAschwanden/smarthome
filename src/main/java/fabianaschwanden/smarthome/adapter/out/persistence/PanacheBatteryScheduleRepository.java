package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatteryScheduleType;
import fabianaschwanden.smarthome.domain.port.out.batteryschedule.BatteryScheduleRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Driven Adapter — übersetzt zwischen Domänen-Modell und JPA-Entity. */
@ApplicationScoped
public class PanacheBatteryScheduleRepository
        implements BatteryScheduleRepository, PanacheRepositoryBase<BatteryScheduleEntity, UUID> {

    @Override
    @Transactional
    public BatterySchedule save(BatterySchedule schedule) {
        BatteryScheduleEntity entity = findByIdOptional(schedule.id()).orElseGet(BatteryScheduleEntity::new);
        apply(schedule, entity);
        persist(entity);
        return schedule;
    }

    @Override
    public Optional<BatterySchedule> byId(UUID id) {
        return findByIdOptional(id).map(this::toDomain);
    }

    @Override
    public List<BatterySchedule> all() {
        return listAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<BatterySchedule> allEnabled() {
        return list("enabled", true).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        deleteById(id);
    }

    private void apply(BatterySchedule s, BatteryScheduleEntity e) {
        e.id = s.id();
        e.type = s.type().name();
        e.action = s.action().name();
        e.enabled = s.enabled();
        e.atTime = s.time();
        e.weekdays = s.weekdays().isEmpty() ? null
                : s.weekdays().stream().map(Enum::name).collect(Collectors.joining(","));
        e.fireAt = s.fireAt();
    }

    private BatterySchedule toDomain(BatteryScheduleEntity e) {
        return new BatterySchedule(
                e.id,
                BatteryScheduleType.valueOf(e.type),
                RelayState.valueOf(e.action),
                e.enabled,
                e.atTime,
                parseWeekdays(e.weekdays),
                e.fireAt);
    }

    private Set<DayOfWeek> parseWeekdays(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
