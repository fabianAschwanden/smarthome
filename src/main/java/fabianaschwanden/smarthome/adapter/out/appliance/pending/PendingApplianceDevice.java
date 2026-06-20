package fabianaschwanden.smarthome.adapter.out.appliance.pending;

import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDevice;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceUnavailable;

import java.util.Optional;
import java.util.Set;

/**
 * Platzhalter für eine echte Anlage, deren Steuerschnittstelle noch nicht angebunden
 * ist (siehe docs/appliance/SPEC.md). Meldet ihre Funktionen (damit die UI sie zeigt),
 * gilt aber als offline und lehnt Schaltbefehle bewusst ab – keine stille Vortäuschung.
 */
public class PendingApplianceDevice implements ApplianceDevice {

    private final String id;
    private final String name;
    private final String room;
    private final Set<ApplianceFunction> functions;
    private final boolean heated;

    public PendingApplianceDevice(
            String id, String name, String room, Set<ApplianceFunction> functions, boolean heated) {
        this.id = id;
        this.name = name;
        this.room = room;
        this.functions = Set.copyOf(functions);
        this.heated = heated;
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
        return functions;
    }

    @Override
    public boolean heated() {
        return heated;
    }

    @Override
    public void apply(ApplianceFunction function, FunctionState state) {
        throw new ApplianceUnavailable(
                "Anlage '" + name + "': Steuerschnittstelle noch nicht angebunden (docs/appliance/SPEC.md)");
    }

    @Override
    public void applyTargetTemp(int target) {
        throw new ApplianceUnavailable(
                "Anlage '" + name + "': Steuerschnittstelle noch nicht angebunden (docs/appliance/SPEC.md)");
    }

    @Override
    public Optional<State> readState() {
        return Optional.empty();
    }
}
