package fabianaschwanden.smarthome.adapter.in.rest.dto.backup;

import fabianaschwanden.smarthome.domain.model.backup.RestoreSummary;

/** Transport-Objekt des Restore-Ergebnisses (Anzahl übernommener Einträge je Kategorie). */
public record RestoreSummaryDto(
        boolean alertSettings,
        int switchSchedules,
        int batterySchedules,
        int coverSchedules,
        int itemImages) {

    public static RestoreSummaryDto from(RestoreSummary s) {
        return new RestoreSummaryDto(s.alertSettings(), s.switchSchedules(),
                s.batterySchedules(), s.coverSchedules(), s.itemImages());
    }
}
