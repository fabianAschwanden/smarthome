package fabianaschwanden.smarthome.adapter.in.rest.dto.itemimage;

import fabianaschwanden.smarthome.domain.model.itemimage.ItemImage;

/** REST-DTO eines Item-Bilds (publizierte Sprache), nicht das Domänenmodell. */
public record ItemImageDto(String itemId, String dataUrl, String updatedAt) {

    public static ItemImageDto from(ItemImage image) {
        return new ItemImageDto(image.itemId(), image.dataUrl(), image.updatedAt().toString());
    }
}
