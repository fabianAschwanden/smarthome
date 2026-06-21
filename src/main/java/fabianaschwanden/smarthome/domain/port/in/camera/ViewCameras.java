package fabianaschwanden.smarthome.domain.port.in.camera;

import fabianaschwanden.smarthome.domain.model.camera.Camera;

import java.util.List;

/** Treiber-Port: konfigurierte Kameras (Metadaten + Stream-Name fürs Gateway). */
public interface ViewCameras {

    List<Camera> list();
}
