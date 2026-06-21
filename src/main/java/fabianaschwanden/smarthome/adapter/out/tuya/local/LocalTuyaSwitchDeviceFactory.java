package fabianaschwanden.smarthome.adapter.out.tuya.local;

import fabianaschwanden.smarthome.adapter.out.tuya.TuyaConfig;
import fabianaschwanden.smarthome.domain.port.out.tuya.SwitchDevice;
import fabianaschwanden.smarthome.domain.port.out.tuya.SwitchDeviceFactory;
import fabianaschwanden.smarthome.support.tuya.TuyaDiscovery;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Erzeugt echte LAN-Geräte aus der Konfiguration (aktiv bei
 * {@code smarthome.real-devices=true}). Geräte ohne local-key erscheinen als
 * {@link UnconfiguredSwitchDevice} (offline), damit sichtbar bleibt, dass noch
 * Zugangsdaten fehlen.
 */
@ApplicationScoped
@IfBuildProperty(name = "smarthome.real-devices", stringValue = "true")
public class LocalTuyaSwitchDeviceFactory implements SwitchDeviceFactory {

    private static final Logger LOG = Logger.getLogger(LocalTuyaSwitchDeviceFactory.class);

    private final TuyaConfig config;
    private final TuyaDiscovery discovery;

    public LocalTuyaSwitchDeviceFactory(TuyaConfig config, TuyaDiscovery discovery) {
        this.config = config;
        this.discovery = discovery;
    }

    @Override
    public List<SwitchDevice> devices() {
        return config.devices().stream().map(this::toDevice).toList();
    }

    private SwitchDevice toDevice(TuyaConfig.Device d) {
        Optional<String> key = d.localKeyIfPresent();
        if (key.isEmpty()) {
            LOG.warnf("Tuya '%s': kein local-key gesetzt – Gerät bleibt offline", d.name());
            return new UnconfiguredSwitchDevice(d.id(), d.name(), d.room());
        }
        return new LocalTuyaSwitchDevice(
                d.id(), d.name(), d.room(), d.deviceId(),
                key.get(), d.addressOrDiscovery(), d.version(), d.dp(), d.critical(), d.hintOrEmpty(), discovery);
    }
}
