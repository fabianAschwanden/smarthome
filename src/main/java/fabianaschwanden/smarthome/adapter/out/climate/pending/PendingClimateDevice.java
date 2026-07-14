package fabianaschwanden.smarthome.adapter.out.climate.pending;

import fabianaschwanden.smarthome.domain.model.climate.ClimateMode;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateDevice;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateUnavailable;

import java.util.Optional;

/**
 * Platzhalter für eine echte Klimaanlage, deren Steuerschnittstelle noch nicht
 * angebunden ist (siehe docs/climate/SPEC.md). Gilt als offline und lehnt Befehle
 * mit 503 ab – keine stille Vortäuschung.
 */
public class PendingClimateDevice implements ClimateDevice {

    private final String id;
    private final String name;
    private final String room;

    public PendingClimateDevice(String id, String name, String room) {
        this.id = id;
        this.name = name;
        this.room = room;
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
    public void applyPower(boolean on) {
        throw unavailable();
    }

    @Override
    public void applyMode(ClimateMode mode) {
        throw unavailable();
    }

    @Override
    public void applyTargetTemp(int temperature) {
        throw unavailable();
    }

    @Override
    public void applyBoost(boolean on) {
        throw unavailable();
    }

    @Override
    public Optional<State> readState() {
        return Optional.empty();
    }

    private ClimateUnavailable unavailable() {
        return new ClimateUnavailable(
                "Klimaanlage '" + name + "': Steuerschnittstelle noch nicht angebunden (docs/climate/SPEC.md)");
    }
}
