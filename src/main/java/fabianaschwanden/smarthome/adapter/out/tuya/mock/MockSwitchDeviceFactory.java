package fabianaschwanden.smarthome.adapter.out.tuya.mock;

import fabianaschwanden.smarthome.adapter.out.tuya.TuyaConfig;
import fabianaschwanden.smarthome.domain.port.out.tuya.SwitchDevice;
import fabianaschwanden.smarthome.domain.port.out.tuya.SwitchDeviceFactory;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Erzeugt Mock-Schaltgeräte aus der Konfiguration (aktiv, solange nicht gegen echte
 * Geräte gefahren wird). So spiegelt die UI dieselbe Geräteliste wie im Echtbetrieb.
 */
@ApplicationScoped
@UnlessBuildProperty(name = "smarthome.real-devices", stringValue = "true", enableIfMissing = true)
public class MockSwitchDeviceFactory implements SwitchDeviceFactory {

    private final TuyaConfig config;

    public MockSwitchDeviceFactory(TuyaConfig config) {
        this.config = config;
    }

    @Override
    public List<SwitchDevice> devices() {
        return config.devices().stream()
                .map(d -> (SwitchDevice) new MockSwitchDevice(d.id(), d.name(), d.room(), d.critical(), d.hintOrEmpty()))
                .toList();
    }
}
