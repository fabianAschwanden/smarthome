package fabianaschwanden.smarthome.adapter.in.rest.dto.camera;

import fabianaschwanden.smarthome.domain.model.camera.Camera;

/** REST-DTO einer Kamera (publizierte Sprache). Enthält keine RTSP-URL/IP. */
public record CameraDto(String id, String name, String room, String stream) {

    public static CameraDto from(Camera c) {
        return new CameraDto(c.id(), c.name(), c.room(), c.stream());
    }
}
