package fabianaschwanden.smarthome.application.service.applianceschedule;

import fabianaschwanden.smarthome.domain.model.applianceschedule.ApplianceSchedule;
import fabianaschwanden.smarthome.domain.port.in.appliance.ControlAppliances;
import fabianaschwanden.smarthome.domain.port.in.applianceschedule.ManageApplianceSchedules;
import fabianaschwanden.smarthome.domain.port.out.applianceschedule.ApplianceScheduleRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Führt fällige Schaltaufträge für Wellness-Anlagen aus.
 *
 * <p>Der Ausführungsweg liegt bewusst hier und nicht in der Prognose: Sonst gäbe es
 * einen zweiten Schaltpfad neben den bestehenden Zeitsteuerungen – derselbe Grundsatz,
 * nach dem die Ladeempfehlung über die Batterie-Zeitsteuerung geht und der Hitzeschutz
 * über die der Storen.
 *
 * <p>Ein ausgeführter Auftrag wird deaktiviert, nicht gelöscht: So bleibt sichtbar, was
 * geschaltet wurde. Aufgeräumt wird erst, wenn er alt genug ist.
 */
@ApplicationScoped
public class ApplianceScheduleService implements ManageApplianceSchedules {

    private static final Logger LOG = Logger.getLogger(ApplianceScheduleService.class);

    private final ApplianceScheduleRepository repository;
    private final ControlAppliances appliances;
    private final Clock clock;

    @Inject
    public ApplianceScheduleService(ApplianceScheduleRepository repository, ControlAppliances appliances) {
        this(repository, appliances, Clock.systemUTC());
    }

    // Sichtbar fürs Testen: feste Uhr.
    ApplianceScheduleService(
            ApplianceScheduleRepository repository, ControlAppliances appliances, Clock clock) {
        this.repository = repository;
        this.appliances = appliances;
        this.clock = clock;
    }

    @Override
    public List<ApplianceSchedule> all() {
        return repository.all();
    }

    @Override
    public ApplianceSchedule save(ApplianceSchedule schedule) {
        return repository.save(schedule);
    }

    @Override
    public void delete(UUID id) {
        repository.delete(id);
    }

    @Scheduled(every = "{appliance-schedule.tick-interval}")
    void tick() {
        Instant now = clock.instant();
        for (ApplianceSchedule schedule : repository.allEnabled()) {
            if (!schedule.isDue(now)) {
                continue;
            }
            try {
                appliances.switchFunction(schedule.applianceId(), schedule.function(), schedule.state());
                LOG.infof("Wellness-Zeitsteuerung: %s %s -> %s",
                        schedule.applianceId(), schedule.function(), schedule.state());
            } catch (Exception e) {
                // Auch ein Fehlschlag deaktiviert den Auftrag: Sonst versuchte der Ticker
                // es alle paar Sekunden erneut, und ein defektes Geraet fuellte das Log.
                LOG.warnf("Wellness-Zeitsteuerung '%s' fehlgeschlagen: %s", schedule.id(), e.getMessage());
            }
            repository.save(schedule.withEnabled(false));
        }
    }
}
