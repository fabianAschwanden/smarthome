package fabianaschwanden.smarthome.domain.port.in.backup;

import fabianaschwanden.smarthome.domain.model.backup.BackupSnapshot;
import fabianaschwanden.smarthome.domain.model.backup.RestoreSummary;

/**
 * Treiber-Port (Use Case): Nutzerdaten sichern und wiederherstellen. Export liefert
 * den vollständigen Schnappschuss; Restore ERSETZT die vorhandenen Daten je Kategorie
 * (kein Merge – das Backup ist die Wahrheit).
 */
public interface ManageBackup {

    BackupSnapshot exportData();

    RestoreSummary restore(BackupSnapshot snapshot);
}
