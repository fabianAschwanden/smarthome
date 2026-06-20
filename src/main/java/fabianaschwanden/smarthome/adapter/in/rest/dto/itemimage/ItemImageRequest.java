package fabianaschwanden.smarthome.adapter.in.rest.dto.itemimage;

/** Request-Body zum Hinterlegen eines Item-Bilds: {@code data:image/…;base64,…}. */
public record ItemImageRequest(String dataUrl) {
}
