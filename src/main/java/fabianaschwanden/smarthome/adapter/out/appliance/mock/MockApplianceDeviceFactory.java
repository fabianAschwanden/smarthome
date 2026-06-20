package fabianaschwanden.smarthome.adapter.out.appliance.mock;

import fabianaschwanden.smarthome.adapter.out.appliance.ApplianceConfig;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDevice;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDeviceFactory;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashSet;
import java.util.List;

/** Erzeugt Mock-Anlagen aus der Konfiguration (aktiv, solange nicht gegen echte Geräte gefahren wird). */
@ApplicationScoped
@UnlessBuildProperty(name = "smarthome.real-devices", stringValue = "true", enableIfMissing = true)
public class MockApplianceDeviceFactory implements ApplianceDeviceFactory {

    private final ApplianceConfig config;

    public MockApplianceDeviceFactory(ApplianceConfig config) {
        this.config = config;
    }

    @Override
    public List<ApplianceDevice> devices() {
        return config.devices().stream()
                .map(d -> (ApplianceDevice) new MockApplianceDevice(
                        d.id(), d.name(), d.room(), new LinkedHashSet<>(d.functions()),
                        d.heated(), d.tempMin(), d.tempMax()))
                .toList();
    }
}
