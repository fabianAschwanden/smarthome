package fabianaschwanden.smarthome.adapter.in.rest.applianceschedule;

import fabianaschwanden.smarthome.adapter.in.rest.dto.applianceschedule.ApplianceScheduleDto;
import fabianaschwanden.smarthome.domain.port.in.applianceschedule.ManageApplianceSchedules;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.List;
import java.util.UUID;

/**
 * Driving Adapter — anstehende Schaltaufträge der Wellness-Anlagen ansehen und
 * verwerfen. Angelegt werden sie aus der Prognose (UC 15 / F4).
 */
@Path("/api/appliance-schedules")
@Produces(MediaType.APPLICATION_JSON)
public class ApplianceScheduleResource {

    private final ManageApplianceSchedules schedules;

    public ApplianceScheduleResource(ManageApplianceSchedules schedules) {
        this.schedules = schedules;
    }

    @GET
    @Operation(summary = "Anstehende und bereits ausgeführte Schaltaufträge")
    public List<ApplianceScheduleDto> list() {
        return schedules.all().stream().map(ApplianceScheduleDto::from).toList();
    }

    @DELETE
    @Path("{id}")
    @Operation(summary = "Schaltauftrag verwerfen")
    public void delete(@PathParam("id") UUID id) {
        schedules.delete(id);
    }
}
