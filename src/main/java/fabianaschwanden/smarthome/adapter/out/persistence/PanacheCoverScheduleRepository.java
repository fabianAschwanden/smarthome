package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.model.coverschedule.CoverSchedule;
import fabianaschwanden.smarthome.domain.model.coverschedule.CoverScheduleType;
import fabianaschwanden.smarthome.domain.port.out.coverschedule.CoverScheduleRepository;
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
public class PanacheCoverScheduleRepository
        implements CoverScheduleRepository, PanacheRepositoryBase<CoverScheduleEntity, UUID> {

    @Override
    @Transactional
    public CoverSchedule save(CoverSchedule schedule) {
        CoverScheduleEntity entity = findByIdOptional(schedule.id()).orElseGet(CoverScheduleEntity::new);
        apply(schedule, entity);
        persist(entity);
        return schedule;
    }

    @Override
    public Optional<CoverSchedule> byId(UUID id) {
        return findByIdOptional(id).map(this::toDomain);
    }

    @Override
    public List<CoverSchedule> all() {
        return listAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<CoverSchedule> allEnabled() {
        return list("enabled", true).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        deleteById(id);
    }

    private void apply(CoverSchedule s, CoverScheduleEntity e) {
        e.id = s.id();
        e.coverId = s.coverId();
        e.type = s.type().name();
        e.position = s.position();
        e.enabled = s.enabled();
        e.atTime = s.time();
        e.weekdays = s.weekdays().isEmpty() ? null
                : s.weekdays().stream().map(Enum::name).collect(Collectors.joining(","));
        e.fireAt = s.fireAt();
    }

    private CoverSchedule toDomain(CoverScheduleEntity e) {
        return new CoverSchedule(
                e.id,
                e.coverId,
                CoverScheduleType.valueOf(e.type),
                e.position,
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
