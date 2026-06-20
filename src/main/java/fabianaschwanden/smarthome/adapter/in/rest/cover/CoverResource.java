package fabianaschwanden.smarthome.adapter.in.rest.cover;

import fabianaschwanden.smarthome.adapter.in.rest.dto.cover.CoverCommandRequest;
import fabianaschwanden.smarthome.adapter.in.rest.dto.cover.CoverDto;
import fabianaschwanden.smarthome.adapter.in.rest.dto.cover.PositionRequest;
import fabianaschwanden.smarthome.domain.port.in.cover.ControlCovers;
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
@Path("/api/covers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoverResource {

    private final ControlCovers covers;

    public CoverResource(ControlCovers covers) {
        this.covers = covers;
    }

    @GET
    public List<CoverDto> list() {
        return covers.list().stream().map(CoverDto::from).toList();
    }

    @POST
    @Path("{id}/command")
    public CoverDto command(@PathParam("id") String id, @Valid CoverCommandRequest request) {
        return CoverDto.from(covers.command(id, request.command()));
    }

    @POST
    @Path("{id}/position")
    public CoverDto setPosition(@PathParam("id") String id, @Valid PositionRequest request) {
        return CoverDto.from(covers.setPosition(id, request.position()));
    }
}
