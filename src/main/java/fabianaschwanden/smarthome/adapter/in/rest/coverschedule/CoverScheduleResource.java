package fabianaschwanden.smarthome.adapter.in.rest.coverschedule;

import fabianaschwanden.smarthome.adapter.in.rest.dto.coverschedule.CoverScheduleDto;
import fabianaschwanden.smarthome.adapter.in.rest.dto.coverschedule.CreateCoverScheduleRequest;
import fabianaschwanden.smarthome.domain.port.in.coverschedule.ManageCoverSchedules;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

/** Driving Adapter — CRUD der Storen-Zeitsteuerungs-Regeln, keine Geschäftslogik. */
@Path("/api/cover-schedules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoverScheduleResource {

    private final ManageCoverSchedules schedules;

    public CoverScheduleResource(ManageCoverSchedules schedules) {
        this.schedules = schedules;
    }

    @GET
    public List<CoverScheduleDto> list(@QueryParam("coverId") String coverId) {
        return schedules.all().stream()
                .filter(s -> coverId == null || coverId.equals(s.coverId()))
                .map(CoverScheduleDto::from)
                .toList();
    }

    @POST
    public CoverScheduleDto create(@Valid CreateCoverScheduleRequest request) {
        return CoverScheduleDto.from(schedules.save(request.toDomain(UUID.randomUUID())));
    }

    @PUT
    @Path("{id}/enabled/{enabled}")
    public CoverScheduleDto setEnabled(@PathParam("id") UUID id, @PathParam("enabled") boolean enabled) {
        return CoverScheduleDto.from(schedules.setEnabled(id, enabled));
    }

    @DELETE
    @Path("{id}")
    public Response delete(@PathParam("id") UUID id) {
        schedules.delete(id);
        return Response.noContent().build();
    }
}
