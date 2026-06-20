package fabianaschwanden.smarthome.application.service.sensor;

import fabianaschwanden.smarthome.domain.model.sensor.Sensor;
import fabianaschwanden.smarthome.domain.port.in.sensor.ReadSensors;
import fabianaschwanden.smarthome.domain.port.out.sensor.SensorDevice;
import fabianaschwanden.smarthome.domain.port.out.sensor.SensorDeviceFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Application-Service: liest die Umweltsensoren. Hält je Sensor die zuletzt bekannten
 * Werte; ist ein Sensor nicht erreichbar, wird er als {@code offline} mit den letzten
 * Werten gemeldet. Reines Lesen – kein Steuern.
 */
@ApplicationScoped
public class SensorReadService implements ReadSensors {

    private final List<SensorDevice> devices;
    private final Map<String, SensorDevice.Reading> lastKnown = new ConcurrentHashMap<>();
    private final Clock clock;

    @Inject
    public SensorReadService(SensorDeviceFactory factory) {
        this(factory.devices(), Clock.systemUTC());
    }

    // Sichtbar fürs Testen.
    SensorReadService(List<SensorDevice> devices, Clock clock) {
        this.devices = List.copyOf(devices);
        this.clock = clock;
    }

    @Override
    public List<Sensor> list() {
        return devices.stream().map(this::observe).toList();
    }

    private Sensor observe(SensorDevice device) {
        Optional<SensorDevice.Reading> current = device.read();
        if (current.isPresent()) {
            lastKnown.put(device.id(), current.get());
            return Sensor.online(device.id(), device.name(), device.room(),
                    current.get().temperature(), current.get().humidity(), clock.instant());
        }
        SensorDevice.Reading known = lastKnown.get(device.id());
        double temp = known != null ? known.temperature() : Sensor.VALUE_UNKNOWN;
        int hum = known != null ? known.humidity() : Sensor.HUMIDITY_UNKNOWN;
        return Sensor.offline(device.id(), device.name(), device.room(), temp, hum, clock.instant());
    }
}
