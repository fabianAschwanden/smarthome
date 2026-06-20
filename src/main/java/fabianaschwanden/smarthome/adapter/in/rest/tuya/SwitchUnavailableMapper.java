package fabianaschwanden.smarthome.adapter.in.rest.tuya;

import fabianaschwanden.smarthome.domain.port.out.tuya.SwitchUnavailable;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Schaltgerät nicht erreichbar / Adapter nicht betriebsbereit → 503 Service Unavailable. */
@Provider
public class SwitchUnavailableMapper implements ExceptionMapper<SwitchUnavailable> {

    @Override
    public Response toResponse(SwitchUnavailable exception) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(exception.getMessage())
                .build();
    }
}
