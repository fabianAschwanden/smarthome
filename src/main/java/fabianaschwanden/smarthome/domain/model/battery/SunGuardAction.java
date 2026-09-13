package fabianaschwanden.smarthome.domain.model.battery;

/**
 * Was der Ohne-Sonne-Ausschalter bei dieser Prüfung tun soll.
 *
 * <ul>
 *   <li>{@code NOTHING} – nichts zu tun (aus, keine Messwerte, oder Lage unverändert)</li>
 *   <li>{@code ARM} – die Sonne ist da: scharf stellen</li>
 *   <li>{@code TRIP} – die Sonne ist weg: Ladung abschalten</li>
 * </ul>
 */
public enum SunGuardAction {
    NOTHING,
    ARM,
    TRIP
}
