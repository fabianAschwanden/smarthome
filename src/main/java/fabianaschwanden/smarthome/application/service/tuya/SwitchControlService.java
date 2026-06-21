package fabianaschwanden.smarthome.application.service.tuya;

import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import fabianaschwanden.smarthome.domain.model.tuya.TuyaSwitch;
import fabianaschwanden.smarthome.domain.port.in.tuya.ControlSwitches;
import fabianaschwanden.smarthome.domain.port.in.tuya.CriticalConfirmationRequired;
import fabianaschwanden.smarthome.domain.port.in.tuya.SwitchNotFound;
import fabianaschwanden.smarthome.domain.port.out.tuya.SwitchDevice;
import fabianaschwanden.smarthome.domain.port.out.tuya.SwitchDeviceFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-Service: orchestriert das Schalten mehrerer Tuya-Geräte. Hält je
 * Gerät den zuletzt bekannten Zustand, schaltet über den Driven Port und liest den
 * Ist-Zustand zurück; nicht erreichbare Geräte werden als {@code offline} gemeldet.
 * Enthält keine Geschäftsregeln.
 */
@ApplicationScoped
public class SwitchControlService implements ControlSwitches {

    private final Map<String, SwitchDevice> devices = new LinkedHashMap<>();
    private final Map<String, SwitchState> lastKnown = new ConcurrentHashMap<>();
    private final Clock clock;

    @Inject
    public SwitchControlService(SwitchDeviceFactory factory) {
        this(factory.devices(), Clock.systemUTC());
    }

    // Sichtbar fürs Testen (deterministische Zeit, direkte Geräteliste).
    SwitchControlService(List<SwitchDevice> devices, Clock clock) {
        for (SwitchDevice device : devices) {
            this.devices.put(device.id(), device);
            this.lastKnown.put(device.id(), SwitchState.OFF);
        }
        this.clock = clock;
    }

    @Override
    public List<TuyaSwitch> list() {
        return devices.values().stream().map(this::observe).toList();
    }

    @Override
    public TuyaSwitch switchTo(String id, SwitchState state, boolean confirmed) {
        SwitchDevice device = devices.get(id);
        if (device == null) {
            throw new SwitchNotFound(id);
        }
        // Kritische Schalter (z. B. Homecinema = WLAN) nur mit Bestätigung ausschalten.
        if (device.critical() && state == SwitchState.OFF && !confirmed) {
            throw new CriticalConfirmationRequired(id);
        }
        device.apply(state);
        lastKnown.put(id, state);
        return observe(device);
    }

    private TuyaSwitch observe(SwitchDevice device) {
        Optional<SwitchState> current = device.readState();
        if (current.isPresent()) {
            lastKnown.put(device.id(), current.get());
            return TuyaSwitch.online(
                    device.id(), device.name(), device.room(), current.get(), device.critical(), device.hint(),
                    clock.instant());
        }
        SwitchState known = lastKnown.getOrDefault(device.id(), SwitchState.OFF);
        return TuyaSwitch.offline(
                device.id(), device.name(), device.room(), known, device.critical(), device.hint(), clock.instant());
    }
}
