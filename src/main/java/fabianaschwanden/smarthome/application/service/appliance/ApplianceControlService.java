package fabianaschwanden.smarthome.application.service.appliance;

import fabianaschwanden.smarthome.domain.model.appliance.Appliance;
import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.model.appliance.Temperature;
import fabianaschwanden.smarthome.domain.port.in.appliance.ApplianceNotFound;
import fabianaschwanden.smarthome.domain.port.in.appliance.ControlAppliances;
import fabianaschwanden.smarthome.domain.port.in.appliance.FunctionNotSupported;
import fabianaschwanden.smarthome.domain.port.in.appliance.TemperatureNotSupported;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDevice;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDeviceFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-Service: orchestriert das Schalten der Anlagen-Funktionen und die
 * Soll-Temperatur beheizter Anlagen. Hält je Anlage die zuletzt bekannten
 * Funktionszustände und die Temperatur; nicht erreichbare Anlagen werden als
 * {@code offline} mit dem letzten bekannten Stand gemeldet.
 */
@ApplicationScoped
public class ApplianceControlService implements ControlAppliances {

    private final Map<String, ApplianceDevice> devices = new LinkedHashMap<>();
    private final Map<String, Map<ApplianceFunction, FunctionState>> lastKnown = new ConcurrentHashMap<>();
    private final Map<String, Temperature> lastTemp = new ConcurrentHashMap<>();
    private final Clock clock;

    @Inject
    public ApplianceControlService(ApplianceDeviceFactory factory) {
        this(factory.devices(), Clock.systemUTC());
    }

    // Sichtbar fürs Testen.
    ApplianceControlService(List<ApplianceDevice> devices, Clock clock) {
        for (ApplianceDevice device : devices) {
            this.devices.put(device.id(), device);
            Map<ApplianceFunction, FunctionState> initial = new EnumMap<>(ApplianceFunction.class);
            device.functions().forEach(f -> initial.put(f, FunctionState.OFF));
            this.lastKnown.put(device.id(), initial);
        }
        this.clock = clock;
    }

    @Override
    public List<Appliance> list() {
        return devices.values().stream().map(this::observe).toList();
    }

    @Override
    public Appliance switchFunction(String id, ApplianceFunction function, FunctionState state) {
        ApplianceDevice device = require(id);
        if (!device.functions().contains(function)) {
            throw new FunctionNotSupported(id, function);
        }
        device.apply(function, state);
        lastKnown.get(id).put(function, state);
        return observe(device);
    }

    @Override
    public Appliance setTargetTemperature(String id, int target) {
        ApplianceDevice device = require(id);
        if (!device.heated()) {
            throw new TemperatureNotSupported(id);
        }
        // Soll-Temperatur gegen den Bereich der Anlage prüfen, bevor das Gerät angesteuert wird.
        Temperature known = lastTemp.get(id);
        if (known != null) {
            known.requireInRange(target);  // wirft IllegalArgumentException (REST 400) bei ausserhalb
        }
        device.applyTargetTemp(target);
        return observe(device);
    }

    private ApplianceDevice require(String id) {
        ApplianceDevice device = devices.get(id);
        if (device == null) {
            throw new ApplianceNotFound(id);
        }
        return device;
    }

    private Appliance observe(ApplianceDevice device) {
        Optional<ApplianceDevice.State> current = device.readState();
        Map<ApplianceFunction, FunctionState> states;
        Temperature temperature;
        boolean online;
        if (current.isPresent()) {
            states = new EnumMap<>(current.get().functions());
            temperature = current.get().temperature();
            lastKnown.put(device.id(), new EnumMap<>(states));
            if (temperature != null) {
                lastTemp.put(device.id(), temperature);
            }
            online = true;
        } else {
            states = lastKnown.getOrDefault(device.id(), new EnumMap<>(ApplianceFunction.class));
            temperature = lastTemp.get(device.id());
            online = false;
        }
        return new Appliance(device.id(), device.name(), device.room(), online, clock.instant(), states, temperature);
    }
}
