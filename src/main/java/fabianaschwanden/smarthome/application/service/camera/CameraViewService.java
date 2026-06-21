package fabianaschwanden.smarthome.application.service.camera;

import fabianaschwanden.smarthome.domain.model.camera.Camera;
import fabianaschwanden.smarthome.domain.port.in.camera.ViewCameras;
import fabianaschwanden.smarthome.domain.port.out.camera.CameraProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Application-Service: liefert die konfigurierten Kameras. Reine Metadaten – das Video
 * läuft über das go2rtc-Gateway (WebRTC), nicht durch das Backend.
 */
@ApplicationScoped
public class CameraViewService implements ViewCameras {

    private final CameraProvider provider;

    public CameraViewService(CameraProvider provider) {
        this.provider = provider;
    }

    @Override
    public List<Camera> list() {
        return provider.cameras();
    }
}
