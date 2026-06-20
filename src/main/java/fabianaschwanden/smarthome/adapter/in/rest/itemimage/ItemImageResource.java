package fabianaschwanden.smarthome.adapter.in.rest.itemimage;

import fabianaschwanden.smarthome.adapter.in.rest.dto.itemimage.ItemImageDto;
import fabianaschwanden.smarthome.adapter.in.rest.dto.itemimage.ItemImageRequest;
import fabianaschwanden.smarthome.domain.port.in.itemimage.ItemImageNotFound;
import fabianaschwanden.smarthome.domain.port.in.itemimage.ManageItemImages;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Driving Adapter — Bilder für Items (typ-übergreifend, {@code id} = fachliche
 * Geräte-ID). Keine Geschäftslogik, übersetzt HTTP auf den Use-Case-Port.
 */
@Path("/api/items/{id}/image")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ItemImageResource {

    private final ManageItemImages images;

    public ItemImageResource(ManageItemImages images) {
        this.images = images;
    }

    @GET
    public ItemImageDto get(@PathParam("id") String id) {
        return images.byItemId(id).map(ItemImageDto::from)
                .orElseThrow(() -> new ItemImageNotFound(id));
    }

    @PUT
    public ItemImageDto put(@PathParam("id") String id, ItemImageRequest request) {
        String dataUrl = request == null ? null : request.dataUrl();
        return ItemImageDto.from(images.put(id, dataUrl));
    }

    @DELETE
    public Response delete(@PathParam("id") String id) {
        images.delete(id);
        return Response.noContent().build();
    }
}
