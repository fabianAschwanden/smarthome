package fabianaschwanden.smarthome.domain.port.in.alert;

import fabianaschwanden.smarthome.domain.model.alert.AlertSettings;

/** Treiber-Port (Use Case): Alert-Einstellungen lesen, speichern und testen. */
public interface ManageAlertSettings {

    /** Aktuelle Einstellungen (Default {@link AlertSettings#disabled()}, falls nie gesetzt). */
    AlertSettings current();

    /** Speichert die Einstellungen und liefert den gespeicherten Stand. */
    AlertSettings save(AlertSettings settings);

    /**
     * Sendet eine Test-Benachrichtigung mit den aktuellen Einstellungen.
     *
     * @return {@code true}, wenn der Push abgesetzt werden konnte.
     */
    boolean sendTest();
}
