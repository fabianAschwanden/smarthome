package fabianaschwanden.smarthome.adapter.in.rest.tuya;

import fabianaschwanden.smarthome.domain.port.in.tuya.CriticalConfirmationRequired;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Kritischer Schalter ohne Bestätigung ausgeschaltet → 409 Conflict. */
@Provider
public class CriticalConfirmationRequiredMapper implements ExceptionMapper<CriticalConfirmationRequired> {

    @Override
    public Response toResponse(CriticalConfirmationRequired exception) {
        return Response.status(Response.Status.CONFLICT).entity(exception.getMessage()).build();
    }
}
