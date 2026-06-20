package fabianaschwanden.smarthome.adapter.in.rest.schedule;

import fabianaschwanden.smarthome.adapter.in.rest.dto.schedule.CreateScheduleRequest;
import fabianaschwanden.smarthome.adapter.in.rest.dto.schedule.ScheduleDto;
import fabianaschwanden.smarthome.domain.port.in.schedule.ManageSchedules;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

/** Driving Adapter — CRUD der Zeitsteuerungs-Regeln, keine Geschäftslogik. */
@Path("/api/schedules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ScheduleResource {

    private final ManageSchedules schedules;

    public ScheduleResource(ManageSchedules schedules) {
        this.schedules = schedules;
    }

    @GET
    public List<ScheduleDto> list(@QueryParam("switchId") String switchId) {
        return schedules.forSwitch(switchId).stream().map(ScheduleDto::from).toList();
    }

    @POST
    public ScheduleDto create(@Valid CreateScheduleRequest request) {
        return ScheduleDto.from(schedules.save(request.toDomain(UUID.randomUUID())));
    }

    @PUT
    @Path("{id}/enabled/{enabled}")
    public ScheduleDto setEnabled(@PathParam("id") UUID id, @PathParam("enabled") boolean enabled) {
        return ScheduleDto.from(schedules.setEnabled(id, enabled));
    }

    @DELETE
    @Path("{id}")
    public Response delete(@PathParam("id") UUID id) {
        schedules.delete(id);
        return Response.noContent().build();
    }
}
