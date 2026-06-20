package fabianaschwanden.smarthome.adapter.in.rest.tuya;

import fabianaschwanden.smarthome.domain.port.in.tuya.SwitchNotFound;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Unbekannte Schalter-ID → 404 Not Found. */
@Provider
public class SwitchNotFoundMapper implements ExceptionMapper<SwitchNotFound> {

    @Override
    public Response toResponse(SwitchNotFound exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(exception.getMessage())
                .build();
    }
}
