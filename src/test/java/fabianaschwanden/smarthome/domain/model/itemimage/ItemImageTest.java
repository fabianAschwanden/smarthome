package fabianaschwanden.smarthome.domain.model.itemimage;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class ItemImageTest {

    private final Instant now = Instant.parse("2026-06-22T10:00:00Z");
    private static final String DATA_URL = "data:image/png;base64,AAAA";

    @Test
    void gueltigeInstanzBautKorrekt() {
        ItemImage img = new ItemImage("stehlampe", DATA_URL, now);
        assertEquals("stehlampe", img.itemId());
        assertEquals(DATA_URL, img.dataUrl());
        assertEquals(now, img.updatedAt());
    }

    @Test
    void itemIdDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class, () -> new ItemImage(null, DATA_URL, now));
        assertThrows(IllegalArgumentException.class, () -> new ItemImage(" ", DATA_URL, now));
    }

    @Test
    void dataUrlDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class, () -> new ItemImage("id", null, now));
        assertThrows(IllegalArgumentException.class, () -> new ItemImage("id", " ", now));
    }

    @Test
    void dataUrlMussBildSein() {
        assertThrows(IllegalArgumentException.class, () -> new ItemImage("id", "data:text/plain;base64,AAAA", now));
        assertThrows(IllegalArgumentException.class, () -> new ItemImage("id", "https://example.com/x.png", now));
    }

    @Test
    void dataUrlZuGrossWirft() {
        String tooLong = "data:image/png;base64," + "A".repeat(ItemImage.MAX_DATA_URL_LENGTH);
        assertThrows(IllegalArgumentException.class, () -> new ItemImage("id", tooLong, now));
    }

    @Test
    void updatedAtDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class, () -> new ItemImage("id", DATA_URL, null));
    }
}
