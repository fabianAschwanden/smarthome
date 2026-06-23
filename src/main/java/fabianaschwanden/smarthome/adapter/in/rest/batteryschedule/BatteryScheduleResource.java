package fabianaschwanden.smarthome.adapter.in.rest.batteryschedule;

import fabianaschwanden.smarthome.adapter.in.rest.dto.batteryschedule.BatteryScheduleDto;
import fabianaschwanden.smarthome.adapter.in.rest.dto.batteryschedule.CreateBatteryScheduleRequest;
import fabianaschwanden.smarthome.domain.port.in.batteryschedule.ManageBatterySchedules;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

/** Driving Adapter — CRUD der Batterie-Zeitsteuerungs-Regeln, keine Geschäftslogik. */
@Path("/api/battery-schedules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BatteryScheduleResource {

    private final ManageBatterySchedules schedules;

    public BatteryScheduleResource(ManageBatterySchedules schedules) {
        this.schedules = schedules;
    }

    @GET
    public List<BatteryScheduleDto> list() {
        return schedules.all().stream().map(BatteryScheduleDto::from).toList();
    }

    @POST
    public BatteryScheduleDto create(@Valid CreateBatteryScheduleRequest request) {
        return BatteryScheduleDto.from(schedules.save(request.toDomain(UUID.randomUUID())));
    }

    @PUT
    @Path("{id}/enabled/{enabled}")
    public BatteryScheduleDto setEnabled(@PathParam("id") UUID id, @PathParam("enabled") boolean enabled) {
        return BatteryScheduleDto.from(schedules.setEnabled(id, enabled));
    }

    @DELETE
    @Path("{id}")
    public Response delete(@PathParam("id") UUID id) {
        schedules.delete(id);
        return Response.noContent().build();
    }
}
