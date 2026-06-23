package fabianaschwanden.smarthome.adapter.in.rest.batteryschedule;

import fabianaschwanden.smarthome.domain.port.in.batteryschedule.BatteryScheduleNotFound;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Unbekannte Batterie-Schedule-ID → 404 Not Found. */
@Provider
public class BatteryScheduleNotFoundMapper implements ExceptionMapper<BatteryScheduleNotFound> {

    @Override
    public Response toResponse(BatteryScheduleNotFound exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(exception.getMessage())
                .build();
    }
}
