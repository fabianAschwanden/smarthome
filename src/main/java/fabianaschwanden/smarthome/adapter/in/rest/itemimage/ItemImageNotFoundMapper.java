package fabianaschwanden.smarthome.adapter.in.rest.itemimage;

import fabianaschwanden.smarthome.domain.port.in.itemimage.ItemImageNotFound;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Kein Bild für das Item hinterlegt → 404 Not Found. */
@Provider
public class ItemImageNotFoundMapper implements ExceptionMapper<ItemImageNotFound> {

    @Override
    public Response toResponse(ItemImageNotFound exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(exception.getMessage())
                .build();
    }
}
