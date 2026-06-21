package fabianaschwanden.smarthome.domain.port.out.camera;

import fabianaschwanden.smarthome.domain.model.camera.Camera;

import java.util.List;

/**
 * Getriebener Port: liefert die konfigurierten Kameras. Der Adapter liest die
 * Konfiguration und bildet sie auf die Domäne ab.
 */
public interface CameraProvider {

    List<Camera> cameras();
}
