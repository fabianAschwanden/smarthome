package fabianaschwanden.smarthome.adapter.in.rest.appliance;

import fabianaschwanden.smarthome.domain.port.in.appliance.TemperatureNotSupported;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Anlage ohne Temperatur-Steuerung → 400 Bad Request. */
@Provider
public class TemperatureNotSupportedMapper implements ExceptionMapper<TemperatureNotSupported> {

    @Override
    public Response toResponse(TemperatureNotSupported exception) {
        return Response.status(Response.Status.BAD_REQUEST).entity(exception.getMessage()).build();
    }
}
