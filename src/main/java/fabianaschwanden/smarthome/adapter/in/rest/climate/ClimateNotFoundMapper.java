package fabianaschwanden.smarthome.adapter.in.rest.climate;

import fabianaschwanden.smarthome.domain.port.in.climate.ClimateNotFound;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Unbekannte Klima-ID → 404 Not Found. */
@Provider
public class ClimateNotFoundMapper implements ExceptionMapper<ClimateNotFound> {

    @Override
    public Response toResponse(ClimateNotFound exception) {
        return Response.status(Response.Status.NOT_FOUND).entity(exception.getMessage()).build();
    }
}
