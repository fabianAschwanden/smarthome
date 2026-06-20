package fabianaschwanden.smarthome.adapter.in.rest.cover;

import fabianaschwanden.smarthome.domain.port.in.cover.CoverNotFound;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Unbekannte Store-ID → 404 Not Found. */
@Provider
public class CoverNotFoundMapper implements ExceptionMapper<CoverNotFound> {

    @Override
    public Response toResponse(CoverNotFound exception) {
        return Response.status(Response.Status.NOT_FOUND).entity(exception.getMessage()).build();
    }
}
