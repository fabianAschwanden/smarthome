package fabianaschwanden.smarthome.adapter.out.appliance.mock;

import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.model.appliance.Temperature;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDevice;
import org.jboss.logging.Logger;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Mock-Anlage für Entwicklung/Test: hält Funktionszustände und – bei beheizten
 * Anlagen – die Soll-Temperatur im Speicher, immer „erreichbar". Wird von
 * {@link MockApplianceDeviceFactory} je konfigurierter Anlage erzeugt.
 */
public class MockApplianceDevice implements ApplianceDevice {

    private static final Logger LOG = Logger.getLogger(MockApplianceDevice.class);

    private final String id;
    private final String name;
    private final String room;
    private final Map<ApplianceFunction, FunctionState> states = new EnumMap<>(ApplianceFunction.class);
    private final boolean heated;
    private final int tempMin;
    private final int tempMax;
    private volatile int target;

    public MockApplianceDevice(
            String id, String name, String room, Set<ApplianceFunction> functions,
            boolean heated, int tempMin, int tempMax) {
        this.id = id;
        this.name = name;
        this.room = room;
        functions.forEach(f -> states.put(f, FunctionState.OFF));
        this.heated = heated;
        this.tempMin = tempMin;
        this.tempMax = tempMax;
        this.target = Math.min(Math.max(22, tempMin), tempMax);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String room() {
        return room;
    }

    @Override
    public Set<ApplianceFunction> functions() {
        return states.keySet();
    }

    @Override
    public boolean heated() {
        return heated;
    }

    @Override
    public void apply(ApplianceFunction function, FunctionState state) {
        states.put(function, state);
        LOG.infof("[mock] Anlage '%s' (%s): %s -> %s", name, id, function, state);
    }

    @Override
    public void applyTargetTemp(int target) {
        if (target < tempMin || target > tempMax) {
            throw new IllegalArgumentException(
                    "Temperatur muss " + tempMin + ".." + tempMax + " °C sein, war " + target);
        }
        this.target = target;
        LOG.infof("[mock] Anlage '%s' (%s): Soll-Temperatur -> %d°C", name, id, target);
    }

    @Override
    public Optional<State> readState() {
        Temperature temp = heated
                ? new Temperature(target, target - 1, tempMin, tempMax)  // simulierte Ist-Temp nahe Soll
                : null;
        return Optional.of(new State(new EnumMap<>(states), temp));
    }
}
