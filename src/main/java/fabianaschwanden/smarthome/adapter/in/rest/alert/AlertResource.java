package fabianaschwanden.smarthome.adapter.in.rest.alert;

import fabianaschwanden.smarthome.adapter.in.rest.dto.alert.AlertSettingsDto;
import fabianaschwanden.smarthome.domain.port.in.alert.ManageAlertSettings;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Driving Adapter — liest/speichert die Alert-Einstellungen und sendet Test-Pushes. */
@Path("/api/alert-settings")
@Produces(MediaType.APPLICATION_JSON)
public class AlertResource {

    private final ManageAlertSettings alerts;

    public AlertResource(ManageAlertSettings alerts) {
        this.alerts = alerts;
    }

    @GET
    public AlertSettingsDto get() {
        return AlertSettingsDto.from(alerts.current());
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public AlertSettingsDto update(AlertSettingsDto dto) {
        return AlertSettingsDto.from(alerts.save(dto.toDomain()));
    }

    /** Sendet eine Test-Benachrichtigung mit den aktuellen Einstellungen. */
    @POST
    @Path("/test")
    public Response test() {
        boolean sent = alerts.sendTest();
        return sent
                ? Response.noContent().build()
                : Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Push nicht möglich – aktiv? Topic gesetzt?\"}")
                        .type(MediaType.APPLICATION_JSON)
                        .build();
    }
}
