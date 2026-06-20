package fabianaschwanden.smarthome.adapter.in.rest.safety;

import fabianaschwanden.smarthome.adapter.in.rest.dto.safety.SmokeDetectorDto;
import fabianaschwanden.smarthome.domain.port.in.safety.ReadSafety;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** Driving Adapter — liefert die Sicherheitsmelder (nur lesend). */
@Path("/api/safety")
@Produces(MediaType.APPLICATION_JSON)
public class SafetyResource {

    private final ReadSafety safety;

    public SafetyResource(ReadSafety safety) {
        this.safety = safety;
    }

    @GET
    @Path("/smoke")
    public List<SmokeDetectorDto> smoke() {
        return safety.smokeDetectors().stream().map(SmokeDetectorDto::from).toList();
    }
}
