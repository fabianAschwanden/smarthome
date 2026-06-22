package fabianaschwanden.smarthome.application.service.alert;

import fabianaschwanden.smarthome.domain.model.alert.AlertSettings;
import fabianaschwanden.smarthome.domain.model.safety.AlarmState;
import fabianaschwanden.smarthome.domain.model.safety.SmokeDetector;
import fabianaschwanden.smarthome.domain.port.in.alert.ManageAlertSettings;
import fabianaschwanden.smarthome.domain.port.in.safety.ReadSafety;
import fabianaschwanden.smarthome.domain.port.out.alert.AlertPublisher;
import fabianaschwanden.smarthome.domain.port.out.alert.AlertSettingsRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashSet;
import java.util.Set;

/**
 * Verwaltet die Alert-Einstellungen und sendet bei kritischen Alarmen einen Push.
 *
 * <p>Ein Scheduler prüft periodisch die Rauchmelder. Beim Übergang OK→ALARM wird
 * EINMALIG gepusht (Flankenerkennung über {@link #alarmsSeen}); solange der Alarm
 * ansteht, wird nicht erneut gepusht. Klingt der Alarm ab, wird der Melder wieder
 * „scharf" für die nächste Flanke. So gibt es keine Push-Flut.
 */
@ApplicationScoped
public class AlertService implements ManageAlertSettings {

    private final AlertSettingsRepository repository;
    private final AlertPublisher publisher;
    private final ReadSafety safety;

    /** IDs der Melder, für die der aktuelle Alarm bereits gepusht wurde. */
    private final Set<String> alarmsSeen = new HashSet<>();

    public AlertService(AlertSettingsRepository repository, AlertPublisher publisher, ReadSafety safety) {
        this.repository = repository;
        this.publisher = publisher;
        this.safety = safety;
    }

    @Override
    public AlertSettings current() {
        return repository.load().orElseGet(AlertSettings::disabled);
    }

    @Override
    public AlertSettings save(AlertSettings settings) {
        repository.save(settings);
        return settings;
    }

    @Override
    public boolean sendTest() {
        return publisher.publish(
                current(),
                "Smart Home – Test",
                "Test-Benachrichtigung. Push ist korrekt eingerichtet.",
                false);
    }

    /** Prüft die Rauchmelder und pusht neu aufgetretene Alarme (Flankenerkennung). */
    @Scheduled(every = "{alert.tick-interval}")
    synchronized void checkAlarms() {
        AlertSettings settings = current();
        if (!settings.canPush()) {
            return;
        }
        for (SmokeDetector sm : safety.smokeDetectors()) {
            boolean inAlarm = sm.alarm() == AlarmState.ALARM;
            if (inAlarm && alarmsSeen.add(sm.id())) {
                // Neue OK->ALARM-Flanke: einmalig pushen.
                publisher.publish(
                        settings,
                        "🔥 Rauchalarm – " + sm.name(),
                        (sm.room().isBlank() ? sm.name() : sm.room()) + ": Rauch erkannt!",
                        true);
            } else if (!inAlarm) {
                // Entwarnung: wieder scharf für die nächste Flanke.
                alarmsSeen.remove(sm.id());
            }
        }
    }
}
