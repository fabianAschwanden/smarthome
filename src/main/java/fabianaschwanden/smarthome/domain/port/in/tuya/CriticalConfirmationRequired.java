package fabianaschwanden.smarthome.domain.port.in.tuya;

/**
 * Das Ausschalten eines kritischen Schalters wurde ohne Bestätigung versucht.
 * Kritische Schalter (z. B. Homecinema, der auch das WLAN versorgt) dürfen nur mit
 * ausdrücklicher Bestätigung ausgeschaltet werden – sonst kappt man die Steuerung.
 * Der REST-Adapter bildet das auf 409 ab.
 */
public class CriticalConfirmationRequired extends RuntimeException {

    public CriticalConfirmationRequired(String id) {
        super("Schalter '" + id + "' ist kritisch – AUS nur mit Bestätigung (kappt sonst das WLAN/die Steuerung)");
    }
}
