package fabianaschwanden.smarthome.application.service.climate;

import fabianaschwanden.smarthome.domain.model.climate.Climate;
import fabianaschwanden.smarthome.domain.model.climate.ClimateMode;
import fabianaschwanden.smarthome.domain.port.in.climate.ClimateDeactivated;
import fabianaschwanden.smarthome.domain.port.in.climate.ClimateNotFound;
import fabianaschwanden.smarthome.domain.port.out.activation.DeviceActivationRepository;
import fabianaschwanden.smarthome.domain.model.activation.DeviceKind;
import fabianaschwanden.smarthome.domain.port.in.climate.ControlClimate;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateDevice;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateDeviceFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-Service: orchestriert das Steuern der Klimaanlagen. Hält je Gerät den
 * zuletzt bekannten Zustand, gibt Befehle über den Driven Port und liest zurück;
 * nicht erreichbare Geräte werden als {@code offline} gemeldet.
 */
@ApplicationScoped
public class ClimateControlService implements ControlClimate {

    private static final int DEFAULT_TARGET = 22;

    private static final Logger LOG = Logger.getLogger(ClimateControlService.class);

    private final Map<String, ClimateDevice> devices = new LinkedHashMap<>();
    private final Map<String, ClimateDevice.State> lastKnown = new ConcurrentHashMap<>();
    private final DeviceActivationRepository activation;
    private final Clock clock;

    @Inject
    public ClimateControlService(ClimateDeviceFactory factory, DeviceActivationRepository activation) {
        this(factory.devices(), activation, Clock.systemUTC());
    }

    // Sichtbar fürs Testen.
    ClimateControlService(List<ClimateDevice> devices, DeviceActivationRepository activation, Clock clock) {
        this.activation = activation;
        for (ClimateDevice device : devices) {
            this.devices.put(device.id(), device);
            this.lastKnown.put(device.id(), new ClimateDevice.State(
                    false, false, ClimateMode.AUTO, DEFAULT_TARGET,
                    Climate.TEMP_UNKNOWN, Climate.TEMP_UNKNOWN));
        }
        this.clock = clock;
    }

    @Override
    public List<Climate> list() {
        Set<String> deactivated = activation.deactivated(DeviceKind.CLIMATE);
        return devices.values().stream()
                .map(device -> deactivated.contains(device.id()) ? dormant(device) : observe(device))
                .toList();
    }

    /**
     * Stillgelegt wird die Anlage nicht mehr angesprochen - gar nicht. Ueber den Winter
     * vom Strom ist sie nicht kaputt; ohne diese Sperre fragte der Sidecar sie trotzdem
     * bei jedem Aufruf ab, und aus einer stillgelegten Anlage wuerde eine dauernd
     * «nicht erreichbare».
     */
    @Override
    public Climate setActive(String id, boolean active) {
        ClimateDevice device = require(id);
        if (active) {
            activation.reactivate(DeviceKind.CLIMATE, id);
            LOG.infof("Klimaanlage '%s' wieder in Betrieb genommen", id);
            return observe(device);
        }
        activation.deactivate(DeviceKind.CLIMATE, id, clock.instant());
        LOG.infof("Klimaanlage '%s' stillgelegt - wird nicht mehr angesprochen", id);
        return dormant(device);
    }

    @Override
    public Climate setPower(String id, boolean on) {
        ClimateDevice device = requireActive(id);
        device.applyPower(on);
        return observe(device);
    }

    @Override
    public Climate setMode(String id, ClimateMode mode) {
        ClimateDevice device = requireActive(id);
        device.applyMode(mode);
        return observe(device);
    }

    @Override
    public Climate setTargetTemp(String id, int temperature) {
        ClimateDevice device = requireActive(id);
        device.applyTargetTemp(Climate.requireValidTarget(temperature));
        return observe(device);
    }

    @Override
    public Climate setBoost(String id, boolean on) {
        ClimateDevice device = requireActive(id);
        device.applyBoost(on);
        return observe(device);
    }

    private ClimateDevice require(String id) {
        ClimateDevice device = devices.get(id);
        if (device == null) {
            throw new ClimateNotFound(id);
        }
        return device;
    }

    private ClimateDevice requireActive(String id) {
        ClimateDevice device = require(id);
        if (activation.deactivated(DeviceKind.CLIMATE).contains(id)) {
            throw new ClimateDeactivated(id);
        }
        return device;
    }

    /** Stillgelegte Anlage: letzter bekannter Stand, ohne das Gerät zu berühren. */
    private Climate dormant(ClimateDevice device) {
        ClimateDevice.State state = lastKnown.get(device.id());
        return new Climate(device.id(), device.name(), device.room(),
                state.power(), state.boost(), state.mode(), state.targetTemp(), state.currentTemp(),
                state.outdoorTemp(), false, false, clock.instant());
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
                state.power(), state.boost(), state.mode(), state.targetTemp(), state.currentTemp(),
                state.outdoorTemp(), online, true, clock.instant());
    }
}
