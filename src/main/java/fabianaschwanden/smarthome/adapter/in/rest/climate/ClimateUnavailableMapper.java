package fabianaschwanden.smarthome.adapter.in.rest.climate;

import fabianaschwanden.smarthome.domain.port.out.climate.ClimateUnavailable;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Klimaanlage nicht erreichbar / Schnittstelle nicht angebunden → 503 Service Unavailable. */
@Provider
public class ClimateUnavailableMapper implements ExceptionMapper<ClimateUnavailable> {

    @Override
    public Response toResponse(ClimateUnavailable exception) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(exception.getMessage()).build();
    }
}
