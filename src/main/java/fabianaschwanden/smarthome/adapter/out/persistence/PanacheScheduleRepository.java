package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.model.schedule.ScheduleType;
import fabianaschwanden.smarthome.domain.model.schedule.SwitchSchedule;
import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import fabianaschwanden.smarthome.domain.port.out.schedule.ScheduleRepository;
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
public class PanacheScheduleRepository
        implements ScheduleRepository, PanacheRepositoryBase<SwitchScheduleEntity, UUID> {

    @Override
    @Transactional
    public SwitchSchedule save(SwitchSchedule schedule) {
        SwitchScheduleEntity entity = findByIdOptional(schedule.id()).orElseGet(SwitchScheduleEntity::new);
        apply(schedule, entity);
        persist(entity);
        return schedule;
    }

    @Override
    public Optional<SwitchSchedule> byId(UUID id) {
        return findByIdOptional(id).map(this::toDomain);
    }

    @Override
    public List<SwitchSchedule> forSwitch(String switchId) {
        return list("switchId", switchId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<SwitchSchedule> all() {
        return listAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<SwitchSchedule> allEnabled() {
        return list("enabled", true).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        deleteById(id);
    }

    private void apply(SwitchSchedule s, SwitchScheduleEntity e) {
        e.id = s.id();
        e.switchId = s.switchId();
        e.type = s.type().name();
        e.action = s.action().name();
        e.enabled = s.enabled();
        e.atTime = s.time();
        e.weekdays = s.weekdays().isEmpty() ? null
                : s.weekdays().stream().map(Enum::name).collect(Collectors.joining(","));
        e.fireAt = s.fireAt();
        e.windowStart = s.windowStart();
        e.windowEnd = s.windowEnd();
        e.pulseSeconds = s.pulseSeconds();
    }

    private SwitchSchedule toDomain(SwitchScheduleEntity e) {
        return new SwitchSchedule(
                e.id,
                e.switchId,
                ScheduleType.valueOf(e.type),
                SwitchState.valueOf(e.action),
                e.enabled,
                e.atTime,
                parseWeekdays(e.weekdays),
                e.fireAt,
                e.windowStart,
                e.windowEnd,
                e.pulseSeconds);
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
