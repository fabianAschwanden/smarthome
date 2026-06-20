package fabianaschwanden.smarthome.adapter.out.cover.local;

import fabianaschwanden.smarthome.adapter.out.cover.CoverConfig;
import fabianaschwanden.smarthome.domain.port.out.cover.CoverDevice;
import fabianaschwanden.smarthome.domain.port.out.cover.CoverDeviceFactory;
import fabianaschwanden.smarthome.support.tuya.TuyaDiscovery;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Erzeugt echte Tuya-Storen aus der Konfiguration (aktiv bei
 * {@code smarthome.real-devices=true}). Geräte ohne local-key erscheinen als
 * {@link UnconfiguredCoverDevice} (offline).
 */
@ApplicationScoped
@IfBuildProperty(name = "smarthome.real-devices", stringValue = "true")
public class LocalTuyaCoverDeviceFactory implements CoverDeviceFactory {

    private static final Logger LOG = Logger.getLogger(LocalTuyaCoverDeviceFactory.class);

    private final CoverConfig config;
    private final TuyaDiscovery discovery;

    public LocalTuyaCoverDeviceFactory(CoverConfig config, TuyaDiscovery discovery) {
        this.config = config;
        this.discovery = discovery;
    }

    @Override
    public List<CoverDevice> devices() {
        return config.devices().stream().map(this::toDevice).toList();
    }

    private CoverDevice toDevice(CoverConfig.Device d) {
        Optional<String> key = d.localKeyIfPresent();
        if (key.isEmpty()) {
            LOG.warnf("Store '%s': kein local-key gesetzt – bleibt offline", d.name());
            return new UnconfiguredCoverDevice(d.id(), d.name(), d.room());
        }
        return new LocalTuyaCoverDevice(
                d.id(), d.name(), d.room(), d.deviceId(), key.get(),
                d.addressOrDiscovery(), d.version(), d.controlDp(), d.positionDp(), d.stateDp(), discovery);
    }
}
