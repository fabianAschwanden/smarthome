package fabianaschwanden.smarthome.adapter.out.sensor.mock;

import fabianaschwanden.smarthome.adapter.out.sensor.SensorConfig;
import fabianaschwanden.smarthome.domain.port.out.sensor.SensorDevice;
import fabianaschwanden.smarthome.domain.port.out.sensor.SensorDeviceFactory;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/** Erzeugt Mock-Sensoren aus der Konfiguration (aktiv, solange nicht gegen echte Geräte gefahren wird). */
@ApplicationScoped
@UnlessBuildProperty(name = "smarthome.real-devices", stringValue = "true", enableIfMissing = true)
public class MockSensorDeviceFactory implements SensorDeviceFactory {

    private final SensorConfig config;

    public MockSensorDeviceFactory(SensorConfig config) {
        this.config = config;
    }

    @Override
    public List<SensorDevice> devices() {
        return config.devices().stream()
                .map(d -> (SensorDevice) new MockSensorDevice(d.id(), d.name(), d.room()))
                .toList();
    }
}
