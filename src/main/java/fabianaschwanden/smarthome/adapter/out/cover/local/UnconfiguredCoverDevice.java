package fabianaschwanden.smarthome.adapter.out.cover.local;

import fabianaschwanden.smarthome.domain.model.cover.CoverCommand;
import fabianaschwanden.smarthome.domain.port.out.cover.CoverDevice;
import fabianaschwanden.smarthome.domain.port.out.cover.CoverUnavailable;

import java.util.OptionalInt;

/**
 * Platzhalter für eine konfigurierte, aber noch nicht vollständig hinterlegte Store
 * (fehlender local-key): erscheint offline und lehnt Befehle ab.
 */
public class UnconfiguredCoverDevice implements CoverDevice {

    private final String id;
    private final String name;
    private final String room;

    public UnconfiguredCoverDevice(String id, String name, String room) {
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
    public void apply(CoverCommand command) {
        throw new CoverUnavailable("Store '" + name + "': local-key fehlt – Zugangsdaten hinterlegen");
    }

    @Override
    public void setPosition(int position) {
        throw new CoverUnavailable("Store '" + name + "': local-key fehlt – Zugangsdaten hinterlegen");
    }

    @Override
    public OptionalInt readPosition() {
        return OptionalInt.empty();
    }
}
