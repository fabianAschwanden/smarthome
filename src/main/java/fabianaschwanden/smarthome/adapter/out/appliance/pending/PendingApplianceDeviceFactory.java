package fabianaschwanden.smarthome.adapter.out.appliance.pending;

import fabianaschwanden.smarthome.adapter.out.appliance.ApplianceConfig;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDevice;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDeviceFactory;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Erzeugt die Anlagen im Echtbetrieb ({@code smarthome.real-devices=true}). Da die
 * Steuerschnittstelle noch nicht feststeht, sind es {@link PendingApplianceDevice}s
 * (offline, lehnen Befehle ab). Wird durch den echten Adapter ersetzt, sobald die
 * Schnittstelle bekannt ist.
 */
@ApplicationScoped
@IfBuildProperty(name = "smarthome.real-devices", stringValue = "true")
public class PendingApplianceDeviceFactory implements ApplianceDeviceFactory {

    private static final Logger LOG = Logger.getLogger(PendingApplianceDeviceFactory.class);

    private final ApplianceConfig config;

    public PendingApplianceDeviceFactory(ApplianceConfig config) {
        this.config = config;
    }

    @Override
    public List<ApplianceDevice> devices() {
        LOG.info("Anlagen-Schnittstelle noch nicht angebunden – Geräte erscheinen offline");
        return config.devices().stream()
                .map(d -> (ApplianceDevice) new PendingApplianceDevice(
                        d.id(), d.name(), d.room(), new LinkedHashSet<>(d.functions()), d.heated()))
                .toList();
    }
}
