package fabianaschwanden.smarthome.adapter.in.rest.nativeview;

import fabianaschwanden.smarthome.adapter.in.rest.dto.nativeview.NativeViewDto;
import fabianaschwanden.smarthome.domain.port.in.nativeview.ViewNativeViews;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** Driving Adapter — liefert die konfigurierten nativen Weboberflächen (Metadaten). */
@Path("/api/native")
@Produces(MediaType.APPLICATION_JSON)
public class NativeViewResource {

    private final ViewNativeViews views;

    public NativeViewResource(ViewNativeViews views) {
        this.views = views;
    }

    @GET
    public List<NativeViewDto> list() {
        return views.list().stream().map(NativeViewDto::from).toList();
    }
}
