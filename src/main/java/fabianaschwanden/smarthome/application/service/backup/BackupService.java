package fabianaschwanden.smarthome.application.service.backup;

import fabianaschwanden.smarthome.domain.model.backup.BackupSnapshot;
import fabianaschwanden.smarthome.domain.model.backup.RestoreSummary;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;
import fabianaschwanden.smarthome.domain.model.coverschedule.CoverSchedule;
import fabianaschwanden.smarthome.domain.model.itemimage.ItemImage;
import fabianaschwanden.smarthome.domain.model.schedule.SwitchSchedule;
import fabianaschwanden.smarthome.domain.port.in.backup.ManageBackup;
import fabianaschwanden.smarthome.domain.port.out.alert.AlertSettingsRepository;
import fabianaschwanden.smarthome.domain.port.out.batteryschedule.BatteryScheduleRepository;
import fabianaschwanden.smarthome.domain.port.out.coverschedule.CoverScheduleRepository;
import fabianaschwanden.smarthome.domain.port.out.itemimage.ItemImageRepository;
import fabianaschwanden.smarthome.domain.port.out.schedule.ScheduleRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Application-Service: exportiert die Nutzerdaten als Schnappschuss und stellt sie
 * wieder her. Restore ersetzt je Kategorie den Bestand (erst löschen, dann einfügen) –
 * die Kategorien laufen nacheinander; jede Einzeloperation ist im Adapter
 * transaktional.
 */
@ApplicationScoped
public class BackupService implements ManageBackup {

    private static final Logger LOG = Logger.getLogger(BackupService.class);

    private final AlertSettingsRepository alertSettings;
    private final ScheduleRepository switchSchedules;
    private final BatteryScheduleRepository batterySchedules;
    private final CoverScheduleRepository coverSchedules;
    private final ItemImageRepository itemImages;

    public BackupService(
            AlertSettingsRepository alertSettings,
            ScheduleRepository switchSchedules,
            BatteryScheduleRepository batterySchedules,
            CoverScheduleRepository coverSchedules,
            ItemImageRepository itemImages) {
        this.alertSettings = alertSettings;
        this.switchSchedules = switchSchedules;
        this.batterySchedules = batterySchedules;
        this.coverSchedules = coverSchedules;
        this.itemImages = itemImages;
    }

    @Override
    public BackupSnapshot exportData() {
        return new BackupSnapshot(
                alertSettings.load(),
                switchSchedules.all(),
                batterySchedules.all(),
                coverSchedules.all(),
                itemImages.all());
    }

    @Override
    public RestoreSummary restore(BackupSnapshot snapshot) {
        snapshot.alertSettings().ifPresent(alertSettings::save);

        for (SwitchSchedule existing : switchSchedules.all()) {
            switchSchedules.delete(existing.id());
        }
        snapshot.switchSchedules().forEach(switchSchedules::save);

        for (BatterySchedule existing : batterySchedules.all()) {
            batterySchedules.delete(existing.id());
        }
        snapshot.batterySchedules().forEach(batterySchedules::save);

        for (CoverSchedule existing : coverSchedules.all()) {
            coverSchedules.delete(existing.id());
        }
        snapshot.coverSchedules().forEach(coverSchedules::save);

        for (ItemImage existing : itemImages.all()) {
            itemImages.delete(existing.itemId());
        }
        snapshot.itemImages().forEach(itemImages::save);

        RestoreSummary summary = new RestoreSummary(
                snapshot.alertSettings().isPresent(),
                snapshot.switchSchedules().size(),
                snapshot.batterySchedules().size(),
                snapshot.coverSchedules().size(),
                snapshot.itemImages().size());
        LOG.infof("Backup wiederhergestellt: %d Schalter-, %d Batterie-, %d Storen-Zeitpläne, %d Bilder",
                summary.switchSchedules(), summary.batterySchedules(),
                summary.coverSchedules(), summary.itemImages());
        return summary;
    }
}
