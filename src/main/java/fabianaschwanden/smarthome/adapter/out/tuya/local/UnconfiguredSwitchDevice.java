package fabianaschwanden.smarthome.adapter.out.tuya.local;

import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import fabianaschwanden.smarthome.domain.port.out.tuya.SwitchDevice;
import fabianaschwanden.smarthome.domain.port.out.tuya.SwitchUnavailable;

import java.util.Optional;

/**
 * Platzhalter für ein konfiguriertes, aber noch nicht vollständig hinterlegtes Gerät
 * (fehlender local-key). Erscheint in der Liste als dauerhaft „offline" und lehnt
 * Schaltbefehle ab – so ist im UI sichtbar, dass noch Zugangsdaten fehlen.
 */
public class UnconfiguredSwitchDevice implements SwitchDevice {

    private final String id;
    private final String name;
    private final String room;

    public UnconfiguredSwitchDevice(String id, String name, String room) {
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
    public boolean critical() {
        return false; // nie steuerbar (offline) – Kritisch-Flag irrelevant
    }

    @Override
    public void apply(SwitchState state) {
        throw new SwitchUnavailable("Tuya '" + name + "': local-key fehlt – Zugangsdaten hinterlegen");
    }

    @Override
    public Optional<SwitchState> readState() {
        return Optional.empty();
    }
}
