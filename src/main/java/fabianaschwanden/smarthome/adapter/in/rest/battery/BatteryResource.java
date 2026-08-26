package fabianaschwanden.smarthome.adapter.in.rest.battery;

import fabianaschwanden.smarthome.adapter.in.rest.dto.battery.BatteryControlDto;
import fabianaschwanden.smarthome.adapter.in.rest.dto.battery.ChangeModeRequest;
import fabianaschwanden.smarthome.adapter.in.rest.dto.battery.SwitchRelayRequest;
import fabianaschwanden.smarthome.adapter.in.rest.dto.charging.ChargingSessionDto;
import fabianaschwanden.smarthome.domain.port.in.battery.ControlBattery;
import fabianaschwanden.smarthome.domain.port.in.charging.ChargingSessionQuery;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;

/** Driving Adapter — übersetzt HTTP auf den Use-Case-Port, keine Geschäftslogik. */
@Path("/api/battery")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BatteryResource {

    private final ControlBattery battery;

    /** Zeigt die letzten Ladevorgaenge - genug fuer einen Ueberblick, wenig fuer eine Kachel. */
    private static final int DEFAULT_SESSIONS = 10;

    private final ChargingSessionQuery chargingSessions;

    public BatteryResource(ControlBattery battery, ChargingSessionQuery chargingSessions) {
        this.chargingSessions = chargingSessions;
        this.battery = battery;
    }

    @GET
    public BatteryControlDto status() {
        return BatteryControlDto.from(battery.status());
    }

    @PUT
    @Path("/mode")
    public BatteryControlDto changeMode(@Valid ChangeModeRequest request) {
        return BatteryControlDto.from(battery.changeMode(request.mode()));
    }

    @POST
    @Path("/relay")
    public BatteryControlDto switchRelay(@Valid SwitchRelayRequest request) {
        return BatteryControlDto.from(battery.switchRelay(request.state()));
    }

    @GET
    @Path("/charging-sessions")
    @Operation(
            summary = "Letzte Ladevorgaenge samt geschaetzter Energie",
            description = "Die Anlage misst das Lade-Relais nicht separat; die Energie wird aus "
                    + "dem Verbrauchssprung beim Einschalten geschaetzt (estimated=true).")
    public java.util.List<ChargingSessionDto> chargingSessions(@QueryParam("limit") Integer limit) {
        return chargingSessions.recentSessions(limit == null ? DEFAULT_SESSIONS : limit).stream()
                .map(ChargingSessionDto::from)
                .toList();
    }
}
