package fabianaschwanden.smarthome.adapter.in.rest.tuya;

import fabianaschwanden.smarthome.adapter.in.rest.dto.tuya.SwitchCommandRequest;
import fabianaschwanden.smarthome.adapter.in.rest.dto.tuya.SwitchDto;
import fabianaschwanden.smarthome.domain.port.in.tuya.ControlSwitches;
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
@Path("/api/switches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SwitchResource {

    private final ControlSwitches control;

    public SwitchResource(ControlSwitches control) {
        this.control = control;
    }

    @GET
    public List<SwitchDto> list() {
        return control.list().stream().map(SwitchDto::from).toList();
    }

    @POST
    @Path("{id}")
    public SwitchDto switchTo(@PathParam("id") String id, @Valid SwitchCommandRequest request) {
        return SwitchDto.from(control.switchTo(id, request.state(), request.confirm()));
    }
}
