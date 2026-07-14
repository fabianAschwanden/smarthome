package fabianaschwanden.smarthome.application.service.climate;

import fabianaschwanden.smarthome.domain.model.climate.Climate;
import fabianaschwanden.smarthome.domain.model.climate.ClimateMode;
import fabianaschwanden.smarthome.domain.port.in.climate.ClimateNotFound;
import fabianaschwanden.smarthome.domain.port.in.climate.ControlClimate;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateDevice;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateDeviceFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-Service: orchestriert das Steuern der Klimaanlagen. Hält je Gerät den
 * zuletzt bekannten Zustand, gibt Befehle über den Driven Port und liest zurück;
 * nicht erreichbare Geräte werden als {@code offline} gemeldet.
 */
@ApplicationScoped
public class ClimateControlService implements ControlClimate {

    private static final int DEFAULT_TARGET = 22;

    private final Map<String, ClimateDevice> devices = new LinkedHashMap<>();
    private final Map<String, ClimateDevice.State> lastKnown = new ConcurrentHashMap<>();
    private final Clock clock;

    @Inject
    public ClimateControlService(ClimateDeviceFactory factory) {
        this(factory.devices(), Clock.systemUTC());
    }

    // Sichtbar fürs Testen.
    ClimateControlService(List<ClimateDevice> devices, Clock clock) {
        for (ClimateDevice device : devices) {
            this.devices.put(device.id(), device);
            this.lastKnown.put(device.id(), new ClimateDevice.State(
                    false, ClimateMode.AUTO, DEFAULT_TARGET, Climate.TEMP_UNKNOWN, Climate.TEMP_UNKNOWN));
        }
        this.clock = clock;
    }

    @Override
    public List<Climate> list() {
        return devices.values().stream().map(this::observe).toList();
    }

    @Override
    public Climate setPower(String id, boolean on) {
        ClimateDevice device = require(id);
        device.applyPower(on);
        return observe(device);
    }

    @Override
    public Climate setMode(String id, ClimateMode mode) {
        ClimateDevice device = require(id);
        device.applyMode(mode);
        return observe(device);
    }

    @Override
    public Climate setTargetTemp(String id, int temperature) {
        ClimateDevice device = require(id);
        device.applyTargetTemp(Climate.requireValidTarget(temperature));
        return observe(device);
    }

    private ClimateDevice require(String id) {
        ClimateDevice device = devices.get(id);
        if (device == null) {
            throw new ClimateNotFound(id);
        }
        return device;
    }

    private Climate observe(ClimateDevice device) {
        Optional<ClimateDevice.State> current = device.readState();
        ClimateDevice.State state;
        boolean online;
        if (current.isPresent()) {
            state = current.get();
            lastKnown.put(device.id(), state);
            online = true;
        } else {
            state = lastKnown.get(device.id());
            online = false;
        }
        return new Climate(device.id(), device.name(), device.room(),
                state.power(), state.mode(), state.targetTemp(), state.currentTemp(),
                state.outdoorTemp(), online, clock.instant());
    }
}
