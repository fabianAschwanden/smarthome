package fabianaschwanden.smarthome.adapter.out.sensor.local;

import fabianaschwanden.smarthome.domain.port.out.sensor.SensorDevice;

import java.util.Optional;

/**
 * Platzhalter für einen konfigurierten, aber noch nicht vollständig hinterlegten
 * Sensor (fehlender local-key): erscheint offline (keine Messwerte).
 */
public class UnconfiguredSensorDevice implements SensorDevice {

    private final String id;
    private final String name;
    private final String room;

    public UnconfiguredSensorDevice(String id, String name, String room) {
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
