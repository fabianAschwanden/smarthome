package fabianaschwanden.smarthome.domain.model.batteryschedule;

/** Art einer Batterie-Zeitsteuerung. */
public enum BatteryScheduleType {
    /** Feste Uhrzeit (optional an Wochentagen). */
    SCHEDULE,
    /** Einmaliger Auslösezeitpunkt; deaktiviert sich danach selbst. */
    COUNTDOWN
}
