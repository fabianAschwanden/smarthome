package fabianaschwanden.smarthome.application.service.batteryschedule;

import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;
import fabianaschwanden.smarthome.domain.port.in.battery.ControlBattery;
import fabianaschwanden.smarthome.domain.port.in.batteryschedule.BatteryScheduleNotFound;
import fabianaschwanden.smarthome.domain.port.in.batteryschedule.ManageBatterySchedules;
import fabianaschwanden.smarthome.domain.port.out.batteryschedule.BatteryScheduleRepository;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-Service: verwaltet die Batterie-Zeitsteuerungs-Regeln (CRUD) und treibt
 * deren Ausführung per Scheduler-Tick. Eine fällige Regel schaltet das Lade-Relais auf
 * ihre Aktion – und versetzt die Batterie dabei in den MANUAL-Modus, damit die
 * Auto-Logik den Zustand nicht sofort wieder überschreibt.
 *
 * <p>Idempotenz: SCHEDULE feuert höchstens einmal pro Tag-Slot (Merker im Speicher);
 * COUNTDOWN ist einmalig und deaktiviert sich nach dem Auslösen selbst.
 */
@ApplicationScoped
public class BatteryScheduleService implements ManageBatterySchedules {

    private static final Logger LOG = Logger.getLogger(BatteryScheduleService.class);

    private final BatteryScheduleRepository repository;
    private final ControlBattery battery;
    private final Clock clock;

    private final Map<String, String> lastFired = new ConcurrentHashMap<>();

    @Inject
    public BatteryScheduleService(BatteryScheduleRepository repository, ControlBattery battery) {
        this(repository, battery, Clock.systemDefaultZone());
    }

    // Sichtbar fürs Testen (feste Zeit).
    BatteryScheduleService(BatteryScheduleRepository repository, ControlBattery battery, Clock clock) {
        this.repository = repository;
        this.battery = battery;
        this.clock = clock;
    }

    // --- CRUD ---

    @Override
    public List<BatterySchedule> all() {
        return repository.all();
    }

    @Override
    public BatterySchedule save(BatterySchedule schedule) {
        return repository.save(schedule);
    }

    @Override
    public BatterySchedule setEnabled(UUID id, boolean enabled) {
        BatterySchedule current = repository.byId(id).orElseThrow(() -> new BatteryScheduleNotFound(id));
        return repository.save(current.withEnabled(enabled));
    }

    @Override
    public void delete(UUID id) {
        if (repository.byId(id).isEmpty()) {
            throw new BatteryScheduleNotFound(id);
        }
        repository.delete(id);
    }

    // --- Scheduler ---

    @Scheduled(every = "{battery-schedule.tick-interval}")
    void tick() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        for (BatterySchedule schedule : repository.allEnabled()) {
            try {
                evaluate(schedule, now);
            } catch (Exception e) {
                LOG.warnf("Batterie-Zeitsteuerung '%s' fehlgeschlagen: %s", schedule.id(), e.getMessage());
            }
        }
    }

    private void evaluate(BatterySchedule schedule, ZonedDateTime now) {
        Optional<RelayState> action = isDue(schedule, now);
        if (action.isEmpty()) {
            return;
        }
        fire(action.get());
        if (schedule.type() == fabianaschwanden.smarthome.domain.model.batteryschedule.BatteryScheduleType.COUNTDOWN) {
            repository.save(schedule.withEnabled(false));
        }
    }

    /**
     * Reine Fälligkeitsprüfung. Liefert die auszuführende Relais-Aktion, wenn die Regel
     * jetzt feuern soll – sonst {@code empty}. Aktualisiert den Merker für SCHEDULE.
     */
    Optional<RelayState> isDue(BatterySchedule schedule, ZonedDateTime now) {
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
        };
    }

    private Optional<RelayState> once(BatterySchedule schedule, String slot, RelayState action) {
        String key = schedule.id().toString();
        if (slot.equals(lastFired.get(key))) {
            return Optional.empty();
        }
        lastFired.put(key, slot);
        return Optional.of(action);
    }

    /** Schaltet das Relais auf den Zielzustand; MANUAL-Modus sichert den Wert gegen die Auto-Logik. */
    private void fire(RelayState state) {
        battery.changeMode(ControlMode.MANUAL);
        battery.switchRelay(state);
        LOG.infof("Batterie-Zeitsteuerung: Relais -> %s (Modus MANUAL)", state);
    }
}
