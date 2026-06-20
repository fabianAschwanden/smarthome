package fabianaschwanden.smarthome.adapter.in.rest.schedule;

import fabianaschwanden.smarthome.domain.port.in.schedule.ScheduleNotFound;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Unbekannte Schedule-ID → 404 Not Found. */
@Provider
public class ScheduleNotFoundMapper implements ExceptionMapper<ScheduleNotFound> {

    @Override
    public Response toResponse(ScheduleNotFound exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(exception.getMessage())
                .build();
    }
}
