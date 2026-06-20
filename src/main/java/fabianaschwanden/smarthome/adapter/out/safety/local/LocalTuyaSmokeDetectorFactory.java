package fabianaschwanden.smarthome.adapter.out.safety.local;

import fabianaschwanden.smarthome.adapter.out.safety.SafetyConfig;
import fabianaschwanden.smarthome.domain.port.out.safety.SmokeDetectorDevice;
import fabianaschwanden.smarthome.domain.port.out.safety.SmokeDetectorDeviceFactory;
import fabianaschwanden.smarthome.support.tuya.TuyaDiscovery;
import fabianaschwanden.smarthome.support.tuya.TuyaSidecarClient;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Erzeugt echte Tuya-Rauchmelder aus der Konfiguration (aktiv bei
 * {@code smarthome.real-devices=true}). Melder ohne local-key erscheinen als
 * {@link UnconfiguredSmokeDetector} (offline).
 */
@ApplicationScoped
@IfBuildProperty(name = "smarthome.real-devices", stringValue = "true")
public class LocalTuyaSmokeDetectorFactory implements SmokeDetectorDeviceFactory {

    private static final Logger LOG = Logger.getLogger(LocalTuyaSmokeDetectorFactory.class);

    private final SafetyConfig config;
    private final TuyaDiscovery discovery;
    private final TuyaSidecarClient sidecar;

    public LocalTuyaSmokeDetectorFactory(SafetyConfig config, TuyaDiscovery discovery, TuyaSidecarClient sidecar) {
        this.config = config;
        this.discovery = discovery;
        this.sidecar = sidecar;
    }

    @Override
    public List<SmokeDetectorDevice> devices() {
        return config.smoke().stream().map(this::toDevice).toList();
    }

    private SmokeDetectorDevice toDevice(SafetyConfig.Device d) {
        Optional<String> key = d.localKeyIfPresent();
        if (key.isEmpty()) {
            LOG.warnf("Rauchmelder '%s': kein local-key gesetzt – bleibt offline", d.name());
            return new UnconfiguredSmokeDetector(d.id(), d.name(), d.room());
        }
        return new LocalTuyaSmokeDetector(
                d.id(), d.name(), d.room(), d.deviceId(), key.get(), d.addressOrDiscovery(),
                d.version(), d.statusDp(), d.batteryDp(), discovery, sidecar);
    }
}
