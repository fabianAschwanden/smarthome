package fabianaschwanden.smarthome.adapter.in.rest.cover;

import fabianaschwanden.smarthome.domain.port.out.cover.CoverUnavailable;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Store nicht erreichbar / Adapter nicht betriebsbereit → 503 Service Unavailable. */
@Provider
public class CoverUnavailableMapper implements ExceptionMapper<CoverUnavailable> {

    @Override
    public Response toResponse(CoverUnavailable exception) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(exception.getMessage()).build();
    }
}
