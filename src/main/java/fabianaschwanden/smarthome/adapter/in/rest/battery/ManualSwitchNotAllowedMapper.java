package fabianaschwanden.smarthome.adapter.in.rest.battery;

import fabianaschwanden.smarthome.domain.port.in.battery.ManualSwitchNotAllowed;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Manuelles Schalten im Auto-Modus → 409 Conflict (SPEC §5). */
@Provider
public class ManualSwitchNotAllowedMapper implements ExceptionMapper<ManualSwitchNotAllowed> {

    @Override
    public Response toResponse(ManualSwitchNotAllowed exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(exception.getMessage())
                .build();
    }
}
