package fabianaschwanden.smarthome.adapter.out.climate.local;

import fabianaschwanden.smarthome.adapter.out.climate.ClimateConfig;
import fabianaschwanden.smarthome.adapter.out.climate.pending.PendingClimateDevice;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateDevice;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateDeviceFactory;
import fabianaschwanden.smarthome.support.tuya.TuyaDiscovery;
import fabianaschwanden.smarthome.support.tuya.TuyaSidecarClient;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Erzeugt die Klimaanlagen im Echtbetrieb ({@code smarthome.real-devices=true}).
 * Vollständig konfigurierte Midea/NetHome-Plus-Geräte (deviceId/token/key/ip) werden
 * als {@link LocalMideaClimateDevice} über den Sidecar gesteuert; unvollständig
 * konfigurierte bleiben als {@link PendingClimateDevice} offline.
 */
@ApplicationScoped
@IfBuildProperty(name = "smarthome.real-devices", stringValue = "true")
public class LocalMideaClimateDeviceFactory implements ClimateDeviceFactory {

    private static final Logger LOG = Logger.getLogger(LocalMideaClimateDeviceFactory.class);

    private final ClimateConfig config;
    private final TuyaDiscovery discovery;
    private final TuyaSidecarClient sidecar;

    public LocalMideaClimateDeviceFactory(
            ClimateConfig config, TuyaDiscovery discovery, TuyaSidecarClient sidecar) {
        this.config = config;
        this.discovery = discovery;
        this.sidecar = sidecar;
    }

    @Override
    public List<ClimateDevice> devices() {
        return config.devices().stream().map(this::toDevice).toList();
    }

    private ClimateDevice toDevice(ClimateConfig.Device d) {
        if (!d.mideaReady()) {
            LOG.warnf("Klima '%s': deviceId/token/key/ip unvollständig – bleibt offline", d.name());
            return new PendingClimateDevice(d.id(), d.name(), d.room());
        }
        LOG.infof("Klima '%s': Midea/NetHome-Plus über Sidecar", d.name());
        return new LocalMideaClimateDevice(
                d.id(), d.name(), d.room(),
                d.deviceId().orElseThrow(), d.token().orElseThrow(), d.key().orElseThrow(),
                d.address().orElseThrow(), discovery, sidecar);
    }
}
