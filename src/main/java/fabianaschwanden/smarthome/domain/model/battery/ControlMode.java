package fabianaschwanden.smarthome.domain.model.battery;

/**
 * Steuermodus der Batterie. Im {@code MANUAL}-Modus folgt das Relais der letzten
 * Benutzeraktion, im {@code AUTO}-Modus der Überschuss-Logik (siehe SPEC §3).
 */
public enum ControlMode {
    MANUAL,
    AUTO
}
