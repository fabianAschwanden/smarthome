package fabianaschwanden.smarthome.domain.port.in.schedule;

import java.util.UUID;

/** Es gibt keine Zeitsteuerungs-Regel mit der angefragten ID (REST: 404). */
public class ScheduleNotFound extends RuntimeException {

    public ScheduleNotFound(UUID id) {
        super("Keine Zeitsteuerung mit ID '" + id + "'");
    }
}
