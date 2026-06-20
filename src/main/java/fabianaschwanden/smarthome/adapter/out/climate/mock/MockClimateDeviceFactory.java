package fabianaschwanden.smarthome.adapter.out.climate.mock;

import fabianaschwanden.smarthome.adapter.out.climate.ClimateConfig;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateDevice;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateDeviceFactory;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/** Erzeugt Mock-Klimaanlagen aus der Konfiguration (aktiv, solange nicht gegen echte Geräte gefahren wird). */
@ApplicationScoped
@UnlessBuildProperty(name = "smarthome.real-devices", stringValue = "true", enableIfMissing = true)
public class MockClimateDeviceFactory implements ClimateDeviceFactory {

    private final ClimateConfig config;

    public MockClimateDeviceFactory(ClimateConfig config) {
        this.config = config;
    }

    @Override
    public List<ClimateDevice> devices() {
        return config.devices().stream()
                .map(d -> (ClimateDevice) new MockClimateDevice(d.id(), d.name(), d.room()))
                .toList();
    }
}
