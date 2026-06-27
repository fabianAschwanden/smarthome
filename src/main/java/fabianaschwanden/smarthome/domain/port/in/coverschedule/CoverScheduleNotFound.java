package fabianaschwanden.smarthome.domain.port.in.coverschedule;

import java.util.UUID;

/** Es gibt keine Storen-Zeitsteuerungs-Regel mit der angefragten ID (REST: 404). */
public class CoverScheduleNotFound extends RuntimeException {

    public CoverScheduleNotFound(UUID id) {
        super("Keine Storen-Zeitsteuerung mit ID '" + id + "'");
    }
}
