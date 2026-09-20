package fabianaschwanden.smarthome.adapter.in.rest.appliance;

import fabianaschwanden.smarthome.domain.port.in.appliance.ApplianceDeactivated;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/** 409 Conflict: Die Anlage ist stillgelegt – kein Fehler, ein gewählter Zustand. */
@Provider
public class ApplianceDeactivatedMapper implements ExceptionMapper<ApplianceDeactivated> {

    @Override
    public Response toResponse(ApplianceDeactivated e) {
        return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", e.getMessage(), "applianceId", e.applianceId()))
                .build();
    }
}
