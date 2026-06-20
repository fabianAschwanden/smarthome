package fabianaschwanden.smarthome.adapter.in.rest.appliance;

import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceUnavailable;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Anlage nicht erreichbar / Schnittstelle nicht angebunden → 503 Service Unavailable. */
@Provider
public class ApplianceUnavailableMapper implements ExceptionMapper<ApplianceUnavailable> {

    @Override
    public Response toResponse(ApplianceUnavailable exception) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(exception.getMessage()).build();
    }
}
