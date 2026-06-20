package fabianaschwanden.smarthome.adapter.out.sensor.local;

import fabianaschwanden.smarthome.adapter.out.sensor.SensorConfig;
import fabianaschwanden.smarthome.domain.port.out.sensor.SensorDevice;
import fabianaschwanden.smarthome.domain.port.out.sensor.SensorDeviceFactory;
import fabianaschwanden.smarthome.support.tuya.TuyaDiscovery;
import fabianaschwanden.smarthome.support.tuya.TuyaSidecarClient;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Erzeugt echte Tuya-Sensoren aus der Konfiguration (aktiv bei
 * {@code smarthome.real-devices=true}). Sensoren ohne local-key erscheinen als
 * {@link UnconfiguredSensorDevice} (offline).
 */
@ApplicationScoped
@IfBuildProperty(name = "smarthome.real-devices", stringValue = "true")
public class LocalTuyaSensorDeviceFactory implements SensorDeviceFactory {

    private static final Logger LOG = Logger.getLogger(LocalTuyaSensorDeviceFactory.class);

    private final SensorConfig config;
    private final TuyaDiscovery discovery;
    private final TuyaSidecarClient sidecar;

    public LocalTuyaSensorDeviceFactory(SensorConfig config, TuyaDiscovery discovery, TuyaSidecarClient sidecar) {
        this.config = config;
        this.discovery = discovery;
        this.sidecar = sidecar;
    }

    @Override
    public List<SensorDevice> devices() {
        return config.devices().stream().map(this::toDevice).toList();
    }

    private SensorDevice toDevice(SensorConfig.Device d) {
        Optional<String> key = d.localKeyIfPresent();
        if (key.isEmpty()) {
            LOG.warnf("Sensor '%s': kein local-key gesetzt – bleibt offline", d.name());
            return new UnconfiguredSensorDevice(d.id(), d.name(), d.room());
        }
        return new LocalTuyaSensorDevice(
                d.id(), d.name(), d.room(), d.deviceId(), key.get(), d.addressOrDiscovery(),
                d.version(), d.temperatureDp(), d.humidityDp(), d.temperatureScale(), discovery, sidecar);
    }
}
