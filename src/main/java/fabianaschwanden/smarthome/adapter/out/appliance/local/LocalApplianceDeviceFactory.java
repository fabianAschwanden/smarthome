package fabianaschwanden.smarthome.adapter.out.appliance.local;

import fabianaschwanden.smarthome.adapter.out.appliance.ApplianceConfig;
import fabianaschwanden.smarthome.adapter.out.appliance.pending.PendingApplianceDevice;
import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDevice;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDeviceFactory;
import fabianaschwanden.smarthome.support.tuya.TuyaSidecarClient;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Erzeugt die Wellness-Anlagen im Echtbetrieb ({@code smarthome.real-devices=true}).
 * Vollständig konfigurierte Gecko-Anlagen (in.touch2: IP + Identifier) werden als
 * {@link LocalGeckoApplianceDevice} über den Sidecar gesteuert; alle anderen bleiben
 * {@link PendingApplianceDevice} (offline), bis ihre Schnittstelle bekannt ist.
 */
@ApplicationScoped
@IfBuildProperty(name = "smarthome.real-devices", stringValue = "true")
public class LocalApplianceDeviceFactory implements ApplianceDeviceFactory {

    private static final Logger LOG = Logger.getLogger(LocalApplianceDeviceFactory.class);

    private final ApplianceConfig config;
    private final TuyaSidecarClient sidecar;

    public LocalApplianceDeviceFactory(ApplianceConfig config, TuyaSidecarClient sidecar) {
        this.config = config;
        this.sidecar = sidecar;
    }

    @Override
    public List<ApplianceDevice> devices() {
        return config.devices().stream().map(this::toDevice).toList();
    }

    private ApplianceDevice toDevice(ApplianceConfig.Device d) {
        Set<ApplianceFunction> functions = new LinkedHashSet<>(d.functions());
        if (d.geckoReady()) {
            LOG.infof("Anlage '%s': Gecko in.touch2 über Sidecar (%s)", d.name(), d.address().orElseThrow());
            return new LocalGeckoApplianceDevice(
                    d.id(), d.name(), d.room(), functions, d.heated(), d.tempMin(), d.tempMax(),
                    d.address().orElseThrow(), d.geckoIdent().orElseThrow(),
                    d.pumpKey().orElse(null), d.massageKey().orElse(null), d.lightKey().orElse(null),
                    sidecar);
        }
        LOG.warnf("Anlage '%s': Gecko unvollständig konfiguriert – bleibt offline", d.name());
        return new PendingApplianceDevice(d.id(), d.name(), d.room(), functions, d.heated());
    }
}
