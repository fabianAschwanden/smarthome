package fabianaschwanden.smarthome.domain.model.coverschedule;

/** Art einer Storen-Zeitsteuerung. */
public enum CoverScheduleType {
    /** Feste Uhrzeit (optional an Wochentagen). */
    SCHEDULE,
    /** Einmaliger Auslösezeitpunkt; deaktiviert sich danach selbst. */
    COUNTDOWN
}
