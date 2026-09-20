package fabianaschwanden.smarthome.adapter.in.rest.climate;

import fabianaschwanden.smarthome.domain.port.in.climate.ClimateDeactivated;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/** 409 Conflict: Die Anlage ist stillgelegt – kein Fehler, ein gewählter Zustand. */
@Provider
public class ClimateDeactivatedMapper implements ExceptionMapper<ClimateDeactivated> {

    @Override
    public Response toResponse(ClimateDeactivated e) {
        return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", e.getMessage(), "climateId", e.climateId()))
                .build();
    }
}
