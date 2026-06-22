package fabianaschwanden.smarthome.domain.port.out.alert;

import fabianaschwanden.smarthome.domain.model.alert.AlertSettings;

/** Getriebener Port: versendet eine Push-Benachrichtigung (z. B. über ntfy.sh). */
public interface AlertPublisher {

    /**
     * Sendet eine Benachrichtigung an das in {@code settings} hinterlegte Ziel.
     *
     * @param settings aktive Einstellungen (Topic etc.)
     * @param title    kurze Überschrift
     * @param message  Nachrichtentext
     * @param priority {@code true} = kritisch/hohe Priorität (z. B. Rauchalarm)
     * @return {@code true}, wenn der Push erfolgreich abgesetzt wurde
     */
    boolean publish(AlertSettings settings, String title, String message, boolean priority);
}
