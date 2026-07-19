package fabianaschwanden.smarthome.domain.model.backup;

/**
 * Ergebnis eines Restores: wie viele Einträge je Kategorie übernommen wurden und ob
 * Alarm-Einstellungen enthalten waren.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record RestoreSummary(
        boolean alertSettings,
        int switchSchedules,
        int batterySchedules,
        int coverSchedules,
        int itemImages) {
}
