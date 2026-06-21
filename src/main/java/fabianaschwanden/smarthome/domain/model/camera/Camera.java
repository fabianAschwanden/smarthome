package fabianaschwanden.smarthome.domain.model.camera;

/**
 * Eine Kamera als Live-Stream-Quelle. Das Backend liefert nur die Metadaten und den
 * go2rtc-Stream-Namen; das eigentliche Video läuft über das Stream-Gateway (WebRTC),
 * nicht über die App. Reines Domänen-Record (Value Object).
 */
public record Camera(String id, String name, String room, String stream) {

    public Camera {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id darf nicht leer sein");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name darf nicht leer sein");
        }
        if (stream == null || stream.isBlank()) {
            throw new IllegalArgumentException("stream (go2rtc-Name) darf nicht leer sein");
        }
    }
}
