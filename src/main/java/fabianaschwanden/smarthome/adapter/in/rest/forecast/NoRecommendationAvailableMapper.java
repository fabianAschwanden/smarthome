package fabianaschwanden.smarthome.adapter.in.rest.forecast;

import fabianaschwanden.smarthome.domain.port.in.forecast.NoRecommendationAvailable;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Übernehmen ohne vorliegende Empfehlung → 409 Conflict (SPEC §4). */
@Provider
public class NoRecommendationAvailableMapper implements ExceptionMapper<NoRecommendationAvailable> {

    @Override
    public Response toResponse(NoRecommendationAvailable exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(exception.getMessage())
                .build();
    }
}
