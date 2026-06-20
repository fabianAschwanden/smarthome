package fabianaschwanden.smarthome.adapter.in.rest.battery;

import fabianaschwanden.smarthome.adapter.in.rest.dto.battery.BatteryControlDto;
import fabianaschwanden.smarthome.adapter.in.rest.dto.battery.ChangeModeRequest;
import fabianaschwanden.smarthome.adapter.in.rest.dto.battery.SwitchRelayRequest;
import fabianaschwanden.smarthome.domain.port.in.battery.ControlBattery;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Driving Adapter — übersetzt HTTP auf den Use-Case-Port, keine Geschäftslogik. */
@Path("/api/battery")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BatteryResource {

    private final ControlBattery battery;

    public BatteryResource(ControlBattery battery) {
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
}
