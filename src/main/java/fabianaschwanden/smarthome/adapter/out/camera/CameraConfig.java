package fabianaschwanden.smarthome.adapter.out.camera;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;

/**
 * Konfiguration der Kameras unter {@code camera.devices[i].*}. Die RTSP-URL (enthält
 * die Haus-IP) gehört NICHT hierher, sondern in die go2rtc-Config des Gateways
 * (gitignored). Hier steht nur, welcher go2rtc-Stream-Name zu welcher Kamera gehört.
 */
@ConfigMapping(prefix = "camera")
public interface CameraConfig {

    List<Device> devices();

    interface Device {
        String id();

        String name();

        @WithDefault("")
        String room();

        /** Name des Streams im go2rtc-Gateway (z. B. {@code garten}). */
        String stream();
    }
}
