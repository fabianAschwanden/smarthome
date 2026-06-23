package fabianaschwanden.smarthome.domain.port.in.batteryschedule;

import java.util.UUID;

/** Es gibt keine Batterie-Zeitsteuerungs-Regel mit der angefragten ID (REST: 404). */
public class BatteryScheduleNotFound extends RuntimeException {

    public BatteryScheduleNotFound(UUID id) {
        super("Keine Batterie-Zeitsteuerung mit ID '" + id + "'");
    }
}
