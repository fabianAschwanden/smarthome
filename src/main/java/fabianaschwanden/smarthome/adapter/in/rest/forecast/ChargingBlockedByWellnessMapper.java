package fabianaschwanden.smarthome.adapter.in.rest.forecast;

import fabianaschwanden.smarthome.domain.port.in.forecast.ChargingBlockedByWellness;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Laden trotz anstehender Wellness-Heizung → 409 Conflict. */
@Provider
public class ChargingBlockedByWellnessMapper implements ExceptionMapper<ChargingBlockedByWellness> {

    @Override
    public Response toResponse(ChargingBlockedByWellness exception) {
        return Response.status(Response.Status.CONFLICT).entity(exception.getMessage()).build();
    }
}
