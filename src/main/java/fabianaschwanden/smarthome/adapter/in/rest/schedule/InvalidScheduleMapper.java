package fabianaschwanden.smarthome.adapter.in.rest.schedule;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Ungültige Eingabedaten (z. B. fehlende Felder je Schedule-Typ) → 400 Bad Request.
 * Greift für die {@link IllegalArgumentException}, die DTO-Mapping und
 * Domänen-Invarianten am Systemrand werfen.
 */
@Provider
public class InvalidScheduleMapper implements ExceptionMapper<IllegalArgumentException> {

    @Override
    public Response toResponse(IllegalArgumentException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(exception.getMessage())
                .build();
    }
}
