package fabianaschwanden.smarthome.adapter.in.rest.appliance;

import fabianaschwanden.smarthome.domain.port.in.appliance.FunctionNotSupported;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Anlage hat die Funktion nicht → 400 Bad Request. */
@Provider
public class FunctionNotSupportedMapper implements ExceptionMapper<FunctionNotSupported> {

    @Override
    public Response toResponse(FunctionNotSupported exception) {
        return Response.status(Response.Status.BAD_REQUEST).entity(exception.getMessage()).build();
    }
}
