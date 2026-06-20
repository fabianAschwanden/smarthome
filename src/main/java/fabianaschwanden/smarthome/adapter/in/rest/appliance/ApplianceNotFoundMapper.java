package fabianaschwanden.smarthome.adapter.in.rest.appliance;

import fabianaschwanden.smarthome.domain.port.in.appliance.ApplianceNotFound;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Unbekannte Anlagen-ID → 404 Not Found. */
@Provider
public class ApplianceNotFoundMapper implements ExceptionMapper<ApplianceNotFound> {

    @Override
    public Response toResponse(ApplianceNotFound exception) {
        return Response.status(Response.Status.NOT_FOUND).entity(exception.getMessage()).build();
    }
}
