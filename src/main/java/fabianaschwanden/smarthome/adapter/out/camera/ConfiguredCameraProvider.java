package fabianaschwanden.smarthome.adapter.out.camera;

import fabianaschwanden.smarthome.domain.model.camera.Camera;
import fabianaschwanden.smarthome.domain.port.out.camera.CameraProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Adapter: bildet die {@link CameraConfig}-Einträge auf {@link Camera}-Domänenobjekte
 * ab. Die RTSP-URL/IP bleibt im go2rtc-Gateway (gitignored), hier steht nur der
 * Stream-Name.
 */
@ApplicationScoped
public class ConfiguredCameraProvider implements CameraProvider {

    private final CameraConfig config;

    public ConfiguredCameraProvider(CameraConfig config) {
        this.config = config;
    }

    @Override
    public List<Camera> cameras() {
        return config.devices().stream()
                .map(d -> new Camera(d.id(), d.name(), d.room(), d.stream()))
                .toList();
    }
}
