package fabianaschwanden.smarthome.application.service.coverschedule;

import fabianaschwanden.smarthome.domain.model.coverschedule.CoverSchedule;
import fabianaschwanden.smarthome.domain.model.coverschedule.CoverScheduleType;
import fabianaschwanden.smarthome.domain.port.in.cover.ControlCovers;
import fabianaschwanden.smarthome.domain.port.in.coverschedule.CoverScheduleNotFound;
import fabianaschwanden.smarthome.domain.port.in.coverschedule.ManageCoverSchedules;
import fabianaschwanden.smarthome.domain.port.out.coverschedule.CoverScheduleRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-Service: verwaltet die Storen-Zeitsteuerungs-Regeln (CRUD) und treibt
 * deren Ausführung per Scheduler-Tick. Eine fällige Regel fährt die Store auf ihre
 * Zielposition (z. B. nachts geschlossen, morgens kurz auf 98 % „zu" anhalten).
 *
 * <p>Idempotenz: SCHEDULE feuert höchstens einmal pro Tag-Slot (Merker im Speicher);
 * COUNTDOWN ist einmalig und deaktiviert sich nach dem Auslösen selbst.
 */
@ApplicationScoped
public class CoverScheduleService implements ManageCoverSchedules {

    private static final Logger LOG = Logger.getLogger(CoverScheduleService.class);

    private final CoverScheduleRepository repository;
    private final ControlCovers covers;
    private final Clock clock;

    private final Map<String, String> lastFired = new ConcurrentHashMap<>();

    @Inject
    public CoverScheduleService(CoverScheduleRepository repository, ControlCovers covers) {
        this(repository, covers, Clock.systemDefaultZone());
    }

    // Sichtbar fürs Testen (feste Zeit).
    CoverScheduleService(CoverScheduleRepository repository, ControlCovers covers, Clock clock) {
        this.repository = repository;
        this.covers = covers;
        this.clock = clock;
    }

    // --- CRUD ---

    @Override
    public List<CoverSchedule> all() {
        return repository.all();
    }

    @Override
    public CoverSchedule save(CoverSchedule schedule) {
        return repository.save(schedule);
    }

    @Override
    public CoverSchedule setEnabled(UUID id, boolean enabled) {
        CoverSchedule current = repository.byId(id).orElseThrow(() -> new CoverScheduleNotFound(id));
        return repository.save(current.withEnabled(enabled));
    }

    @Override
    public void delete(UUID id) {
        if (repository.byId(id).isEmpty()) {
            throw new CoverScheduleNotFound(id);
        }
        repository.delete(id);
    }

    // --- Scheduler ---

    @Scheduled(every = "{cover-schedule.tick-interval}")
    void tick() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        for (CoverSchedule schedule : repository.allEnabled()) {
            try {
                evaluate(schedule, now);
            } catch (Exception e) {
                LOG.warnf("Storen-Zeitsteuerung '%s' fehlgeschlagen: %s", schedule.id(), e.getMessage());
            }
        }
    }

    private void evaluate(CoverSchedule schedule, ZonedDateTime now) {
        if (!isDue(schedule, now)) {
            return;
        }
        fire(schedule);
        if (schedule.type() == CoverScheduleType.COUNTDOWN) {
            repository.save(schedule.withEnabled(false));
        }
    }

    /**
     * Reine Fälligkeitsprüfung. Liefert {@code true}, wenn die Regel jetzt feuern soll.
     * Aktualisiert den Tag-Slot-Merker für SCHEDULE (höchstens einmal pro Minute-Slot).
     */
    boolean isDue(CoverSchedule schedule, ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime();
        return switch (schedule.type()) {
            case SCHEDULE -> {
                if (!schedule.appliesOn(now.getDayOfWeek())
                        || time.getHour() != schedule.time().getHour()
                        || time.getMinute() != schedule.time().getMinute()) {
                    yield false;
                }
                yield once(schedule, today + "T" + schedule.time());
            }
            case COUNTDOWN -> !now.toInstant().isBefore(schedule.fireAt());
        };
    }

    private boolean once(CoverSchedule schedule, String slot) {
        String key = schedule.id().toString();
        if (slot.equals(lastFired.get(key))) {
            return false;
        }
        lastFired.put(key, slot);
        return true;
    }

    /** Fährt die Store auf die Zielposition (0 = zu, 100 = offen). */
    private void fire(CoverSchedule schedule) {
        covers.setPosition(schedule.coverId(), schedule.position());
        LOG.infof("Storen-Zeitsteuerung: %s -> Position %d", schedule.coverId(), schedule.position());
    }
}
