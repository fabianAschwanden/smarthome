package fabianaschwanden.smarthome.application.service.schedule;

import fabianaschwanden.smarthome.domain.model.schedule.SwitchSchedule;
import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import fabianaschwanden.smarthome.domain.port.in.schedule.ManageSchedules;
import fabianaschwanden.smarthome.domain.port.in.schedule.ScheduleNotFound;
import fabianaschwanden.smarthome.domain.port.in.tuya.ControlSwitches;
import fabianaschwanden.smarthome.domain.port.out.schedule.ScheduleRepository;
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
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-Service: verwaltet die Zeitsteuerungs-Regeln (CRUD) und treibt deren
 * Ausführung per Scheduler. Bewusst in einer (injizierten) Bean gebündelt – wie der
 * Batterie-Service –, damit der {@code @Scheduled}-Tick zuverlässig registriert wird.
 * Die reine Fälligkeitsprüfung {@link #isDue} ist gekapselt und unit-testbar.
 *
 * <p>Idempotenz: SCHEDULE/RANDOM feuern höchstens einmal pro Tag-Slot (Merker im
 * Speicher). COUNTDOWN und INCHING sind einmalig und deaktivieren sich nach dem
 * Auslösen selbst.
 */
@ApplicationScoped
public class ScheduleService implements ManageSchedules {

    private static final Logger LOG = Logger.getLogger(ScheduleService.class);

    private final ScheduleRepository repository;
    private final ControlSwitches switches;
    private final Clock clock;
    private final Random random;

    private final Map<String, String> lastFired = new ConcurrentHashMap<>();
    private final Map<String, LocalTime> randomPick = new ConcurrentHashMap<>();

    @Inject
    public ScheduleService(ScheduleRepository repository, ControlSwitches switches) {
        this(repository, switches, Clock.systemDefaultZone(), new Random());
    }

    // Sichtbar fürs Testen (feste Zeit/Zufall).
    ScheduleService(ScheduleRepository repository, ControlSwitches switches, Clock clock, Random random) {
        this.repository = repository;
        this.switches = switches;
        this.clock = clock;
        this.random = random;
    }

    // --- CRUD (ManageSchedules) ---

    @Override
    public List<SwitchSchedule> forSwitch(String switchId) {
        return repository.forSwitch(switchId);
    }

    @Override
    public SwitchSchedule save(SwitchSchedule schedule) {
        return repository.save(schedule);
    }

    @Override
    public SwitchSchedule setEnabled(UUID id, boolean enabled) {
        SwitchSchedule current = repository.byId(id).orElseThrow(() -> new ScheduleNotFound(id));
        return repository.save(current.withEnabled(enabled));
    }

    @Override
    public void delete(UUID id) {
        if (repository.byId(id).isEmpty()) {
            throw new ScheduleNotFound(id);
        }
        repository.delete(id);
    }

    // --- Scheduler ---

    @Scheduled(every = "{schedule.tick-interval}")
    void tick() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        for (SwitchSchedule schedule : repository.allEnabled()) {
            try {
                evaluate(schedule, now);
            } catch (Exception e) {
                LOG.warnf("Zeitsteuerung '%s' fehlgeschlagen: %s", schedule.id(), e.getMessage());
            }
        }
    }

    private void evaluate(SwitchSchedule schedule, ZonedDateTime now) {
        Optional<SwitchState> action = isDue(schedule, now);
        if (action.isEmpty()) {
            return;
        }
        switch (schedule.type()) {
            case SCHEDULE, RANDOM -> fire(schedule, action.get());
            case COUNTDOWN -> {
                fire(schedule, action.get());
                disable(schedule);
            }
            case INCHING -> {
                fire(schedule, SwitchState.ON);
                scheduleAutoOff(schedule);
                disable(schedule);
            }
        }
    }

    /**
     * Reine Fälligkeitsprüfung. Liefert die auszuführende Aktion, wenn die Regel jetzt
     * feuern soll – sonst {@code empty}. Aktualisiert die Merker für SCHEDULE/RANDOM.
     */
    Optional<SwitchState> isDue(SwitchSchedule schedule, ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime();
        return switch (schedule.type()) {
            case SCHEDULE -> {
                if (!schedule.appliesOn(now.getDayOfWeek())
                        || time.getHour() != schedule.time().getHour()
                        || time.getMinute() != schedule.time().getMinute()) {
                    yield Optional.empty();
                }
                yield once(schedule, today + "T" + schedule.time(), schedule.action());
            }
            case COUNTDOWN -> now.toInstant().isBefore(schedule.fireAt())
                    ? Optional.empty() : Optional.of(schedule.action());
            case RANDOM -> {
                LocalTime pick = randomPick.compute(schedule.id().toString(),
                        (k, existing) -> pickIfNewDay(schedule, today, existing));
                if (pick == null || time.getHour() != pick.getHour() || time.getMinute() != pick.getMinute()) {
                    yield Optional.empty();
                }
                yield once(schedule, today + "T" + pick, schedule.action());
            }
            case INCHING -> Optional.of(SwitchState.ON);
        };
    }

    private Optional<SwitchState> once(SwitchSchedule schedule, String slot, SwitchState action) {
        String key = schedule.id().toString();
        if (slot.equals(lastFired.get(key))) {
            return Optional.empty();
        }
        lastFired.put(key, slot);
        return Optional.of(action);
    }

    private LocalTime pickIfNewDay(SwitchSchedule schedule, LocalDate today, LocalTime existing) {
        String dayKey = schedule.id() + "#" + today;
        if (existing != null && dayKey.equals(lastFired.get("rndDay#" + schedule.id()))) {
            return existing;
        }
        lastFired.put("rndDay#" + schedule.id(), dayKey);
        int from = schedule.windowStart().toSecondOfDay() / 60;
        int to = schedule.windowEnd().toSecondOfDay() / 60;
        if (to <= from) {
            return schedule.windowStart();
        }
        return LocalTime.ofSecondOfDay((from + random.nextInt(to - from)) * 60L);
    }

    private void fire(SwitchSchedule schedule, SwitchState state) {
        // Zeitsteuerung ist die ausdrückliche Absicht des Nutzers -> Bestätigung implizit.
        switches.switchTo(schedule.switchId(), state, true);
        LOG.infof("Zeitsteuerung %s: %s -> %s", schedule.type(), schedule.switchId(), state);
    }

    private void scheduleAutoOff(SwitchSchedule schedule) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(schedule.pulseSeconds() * 1000L);
                switches.switchTo(schedule.switchId(), SwitchState.OFF, true);
                LOG.infof("Inching: %s -> OFF (nach %ds)", schedule.switchId(), schedule.pulseSeconds());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void disable(SwitchSchedule schedule) {
        repository.save(schedule.withEnabled(false));
    }
}
