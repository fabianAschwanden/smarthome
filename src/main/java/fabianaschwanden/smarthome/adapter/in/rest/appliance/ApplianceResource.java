package fabianaschwanden.smarthome.adapter.in.rest.appliance;

import fabianaschwanden.smarthome.adapter.in.rest.dto.appliance.ApplianceDto;
import fabianaschwanden.smarthome.adapter.in.rest.dto.appliance.FunctionCommandRequest;
import fabianaschwanden.smarthome.adapter.in.rest.dto.appliance.TemperatureRequest;
import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.port.in.appliance.ControlAppliances;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** Driving Adapter — übersetzt HTTP auf den Use-Case-Port, keine Geschäftslogik. */
@Path("/api/appliances")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ApplianceResource {

    private final ControlAppliances appliances;

    public ApplianceResource(ControlAppliances appliances) {
        this.appliances = appliances;
    }

    @GET
    public List<ApplianceDto> list() {
        return appliances.list().stream().map(ApplianceDto::from).toList();
    }

    @POST
    @Path("{id}/functions/{function}")
    public ApplianceDto switchFunction(
            @PathParam("id") String id,
            @PathParam("function") ApplianceFunction function,
            @Valid FunctionCommandRequest request) {
        return ApplianceDto.from(appliances.switchFunction(id, function, request.state()));
    }

    @POST
    @Path("{id}/temperature")
    public ApplianceDto setTemperature(@PathParam("id") String id, TemperatureRequest request) {
        return ApplianceDto.from(appliances.setTargetTemperature(id, request.target()));
    }
}
