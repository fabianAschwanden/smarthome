package fabianaschwanden.smarthome.adapter.out.safety.local;

import fabianaschwanden.smarthome.domain.port.out.safety.SmokeDetectorDevice;

import java.util.Optional;

/**
 * Platzhalter für einen konfigurierten, aber noch nicht vollständig hinterlegten
 * Rauchmelder (fehlender local-key): erscheint offline.
 */
public class UnconfiguredSmokeDetector implements SmokeDetectorDevice {

    private final String id;
    private final String name;
    private final String room;

    public UnconfiguredSmokeDetector(String id, String name, String room) {
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
    public Optional<Reading> read() {
        return Optional.empty();
    }
}
