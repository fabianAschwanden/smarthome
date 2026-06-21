package fabianaschwanden.smarthome.adapter.in.rest.camera;

import fabianaschwanden.smarthome.adapter.in.rest.dto.camera.CameraDto;
import fabianaschwanden.smarthome.domain.port.in.camera.ViewCameras;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** Driving Adapter — liefert die konfigurierten Kameras (Metadaten + go2rtc-Stream-Name). */
@Path("/api/cameras")
@Produces(MediaType.APPLICATION_JSON)
public class CameraResource {

    private final ViewCameras cameras;

    public CameraResource(ViewCameras cameras) {
        this.cameras = cameras;
    }

    @GET
    public List<CameraDto> list() {
        return cameras.list().stream().map(CameraDto::from).toList();
    }
}
