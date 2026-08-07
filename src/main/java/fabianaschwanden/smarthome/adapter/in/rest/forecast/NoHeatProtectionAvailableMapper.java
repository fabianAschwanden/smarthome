package fabianaschwanden.smarthome.adapter.in.rest.forecast;

import fabianaschwanden.smarthome.domain.port.in.forecast.NoHeatProtectionAvailable;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Übernehmen ohne vorliegendes Beschattungsfenster → 409 Conflict. */
@Provider
public class NoHeatProtectionAvailableMapper implements ExceptionMapper<NoHeatProtectionAvailable> {

    @Override
    public Response toResponse(NoHeatProtectionAvailable exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(exception.getMessage())
                .build();
    }
}
