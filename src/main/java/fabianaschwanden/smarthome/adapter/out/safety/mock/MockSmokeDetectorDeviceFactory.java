package fabianaschwanden.smarthome.adapter.out.safety.mock;

import fabianaschwanden.smarthome.adapter.out.safety.SafetyConfig;
import fabianaschwanden.smarthome.domain.port.out.safety.SmokeDetectorDevice;
import fabianaschwanden.smarthome.domain.port.out.safety.SmokeDetectorDeviceFactory;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/** Erzeugt Mock-Rauchmelder aus der Konfiguration (aktiv, solange nicht gegen echte Geräte gefahren wird). */
@ApplicationScoped
@UnlessBuildProperty(name = "smarthome.real-devices", stringValue = "true", enableIfMissing = true)
public class MockSmokeDetectorDeviceFactory implements SmokeDetectorDeviceFactory {

    private final SafetyConfig config;

    public MockSmokeDetectorDeviceFactory(SafetyConfig config) {
        this.config = config;
    }

    @Override
    public List<SmokeDetectorDevice> devices() {
        return config.smoke().stream()
                .map(d -> (SmokeDetectorDevice) new MockSmokeDetectorDevice(d.id(), d.name(), d.room()))
                .toList();
    }
}
