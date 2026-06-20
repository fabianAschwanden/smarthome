package fabianaschwanden.smarthome.domain.model.schedule;

/**
 * Art der Zeitsteuerung eines Schalters (vgl. Smart-Life-App):
 * <ul>
 *   <li>{@code SCHEDULE} – feste Uhrzeit an bestimmten Wochentagen (wiederkehrend)</li>
 *   <li>{@code COUNTDOWN} – einmalig nach einer Verzögerung</li>
 *   <li>{@code RANDOM} – zufälliger Zeitpunkt innerhalb eines Zeitfensters (Anwesenheitssimulation)</li>
 *   <li>{@code INCHING} – Impuls: einschalten und nach kurzer Zeit automatisch ausschalten</li>
 * </ul>
 */
public enum ScheduleType {
    SCHEDULE,
    COUNTDOWN,
    RANDOM,
    INCHING
}
