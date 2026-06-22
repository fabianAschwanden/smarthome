package fabianaschwanden.smarthome.domain.model.camera;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class CameraTest {

    @Test
    void gueltigeInstanzBautKorrekt() {
        Camera c = new Camera("cam1", "Eingang", "Aussen", "eingang_stream");
        assertEquals("cam1", c.id());
        assertEquals("Eingang", c.name());
        assertEquals("Aussen", c.room());
        assertEquals("eingang_stream", c.stream());
    }

    @Test
    void idDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class, () -> new Camera(null, "Eingang", "Aussen", "s"));
        assertThrows(IllegalArgumentException.class, () -> new Camera(" ", "Eingang", "Aussen", "s"));
    }

    @Test
    void nameDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class, () -> new Camera("cam1", " ", "Aussen", "s"));
    }

    @Test
    void streamDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class, () -> new Camera("cam1", "Eingang", "Aussen", null));
        assertThrows(IllegalArgumentException.class, () -> new Camera("cam1", "Eingang", "Aussen", " "));
    }
}
