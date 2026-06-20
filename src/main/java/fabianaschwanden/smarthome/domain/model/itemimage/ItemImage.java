package fabianaschwanden.smarthome.domain.model.itemimage;

import java.time.Instant;

/**
 * Ein hinterlegtes Bild für ein Item (Schalter, Store, Klima, Anlage …). Das Bild ist
 * an die fachliche Item-ID gebunden (z. B. {@code stehlampe}) und wird als Data-URL
 * gehalten ({@code data:<mime>;base64,…}). Reines Domänen-Record mit Invarianten im
 * Compact-Constructor – framework-frei.
 */
public record ItemImage(String itemId, String dataUrl, Instant updatedAt) {

    /** Obergrenze der Data-URL-Länge (~2 MB Bild ⇒ ~2.7 MB Base64). Schützt DB & Transport. */
    public static final int MAX_DATA_URL_LENGTH = 3_000_000;

    public ItemImage {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId darf nicht leer sein");
        }
        if (dataUrl == null || dataUrl.isBlank()) {
            throw new IllegalArgumentException("dataUrl darf nicht leer sein");
        }
        if (!dataUrl.startsWith("data:image/")) {
            throw new IllegalArgumentException("dataUrl muss ein Bild-Data-URL sein (data:image/…)");
        }
        if (dataUrl.length() > MAX_DATA_URL_LENGTH) {
            throw new IllegalArgumentException(
                    "Bild zu gross (max " + MAX_DATA_URL_LENGTH + " Zeichen Data-URL)");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt darf nicht null sein");
        }
    }
}
