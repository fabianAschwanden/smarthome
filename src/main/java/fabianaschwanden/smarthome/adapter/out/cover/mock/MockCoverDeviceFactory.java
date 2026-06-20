package fabianaschwanden.smarthome.adapter.out.cover.mock;

import fabianaschwanden.smarthome.adapter.out.cover.CoverConfig;
import fabianaschwanden.smarthome.domain.port.out.cover.CoverDevice;
import fabianaschwanden.smarthome.domain.port.out.cover.CoverDeviceFactory;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/** Erzeugt Mock-Storen aus der Konfiguration (aktiv, solange nicht gegen echte Geräte gefahren wird). */
@ApplicationScoped
@UnlessBuildProperty(name = "smarthome.real-devices", stringValue = "true", enableIfMissing = true)
public class MockCoverDeviceFactory implements CoverDeviceFactory {

    private final CoverConfig config;

    public MockCoverDeviceFactory(CoverConfig config) {
        this.config = config;
    }

    @Override
    public List<CoverDevice> devices() {
        return config.devices().stream()
                .map(d -> (CoverDevice) new MockCoverDevice(d.id(), d.name(), d.room()))
                .toList();
    }
}
