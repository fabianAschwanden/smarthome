package fabianaschwanden.smarthome.domain.model.backup;

import fabianaschwanden.smarthome.domain.model.alert.AlertSettings;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;
import fabianaschwanden.smarthome.domain.model.coverschedule.CoverSchedule;
import fabianaschwanden.smarthome.domain.model.itemimage.ItemImage;
import fabianaschwanden.smarthome.domain.model.schedule.SwitchSchedule;

import java.util.List;
import java.util.Optional;

/**
 * Vollständiger Schnappschuss der Nutzerdaten für Export/Restore: Zeitpläne,
 * Alarm-Einstellungen und Item-Bilder. Bewusst NICHT enthalten: {@code energy_sample}
 * (regeneriert sich über den Sampler, ~390k Zeilen bei 45 Tagen Retention).
 *
 * <p>Value Object: immutable {@code record}; Listen werden defensiv kopiert.
 */
public record BackupSnapshot(
        Optional<AlertSettings> alertSettings,
        List<SwitchSchedule> switchSchedules,
        List<BatterySchedule> batterySchedules,
        List<CoverSchedule> coverSchedules,
        List<ItemImage> itemImages) {

    public BackupSnapshot {
        if (alertSettings == null) {
            alertSettings = Optional.empty();
        }
        switchSchedules = switchSchedules == null ? List.of() : List.copyOf(switchSchedules);
        batterySchedules = batterySchedules == null ? List.of() : List.copyOf(batterySchedules);
        coverSchedules = coverSchedules == null ? List.of() : List.copyOf(coverSchedules);
        itemImages = itemImages == null ? List.of() : List.copyOf(itemImages);
    }
}
