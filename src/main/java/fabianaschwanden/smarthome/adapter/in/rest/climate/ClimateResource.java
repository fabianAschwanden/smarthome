package fabianaschwanden.smarthome.adapter.in.rest.climate;

import fabianaschwanden.smarthome.adapter.in.rest.dto.climate.ClimateDto;
import fabianaschwanden.smarthome.adapter.in.rest.dto.climate.ModeRequest;
import fabianaschwanden.smarthome.adapter.in.rest.dto.climate.PowerRequest;
import fabianaschwanden.smarthome.adapter.in.rest.dto.climate.TargetTempRequest;
import fabianaschwanden.smarthome.domain.port.in.climate.ControlClimate;
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
@Path("/api/climate")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClimateResource {

    private final ControlClimate climate;

    public ClimateResource(ControlClimate climate) {
        this.climate = climate;
    }

    @GET
    public List<ClimateDto> list() {
        return climate.list().stream().map(ClimateDto::from).toList();
    }

    @POST
    @Path("{id}/power")
    public ClimateDto setPower(@PathParam("id") String id, @Valid PowerRequest request) {
        return ClimateDto.from(climate.setPower(id, request.on()));
    }

    @POST
    @Path("{id}/mode")
    public ClimateDto setMode(@PathParam("id") String id, @Valid ModeRequest request) {
        return ClimateDto.from(climate.setMode(id, request.mode()));
    }

    @POST
    @Path("{id}/target")
    public ClimateDto setTarget(@PathParam("id") String id, @Valid TargetTempRequest request) {
        return ClimateDto.from(climate.setTargetTemp(id, request.temperature()));
    }
}
