package fabianaschwanden.smarthome.adapter.out.sensor.mock;

import fabianaschwanden.smarthome.domain.port.out.sensor.SensorDevice;

import java.util.Optional;

/**
 * Mock-Sensor für Entwicklung/Test: liefert plausible Innenraumwerte (21.5 °C, 45 %).
 */
public class MockSensorDevice implements SensorDevice {

    private final String id;
    private final String name;
    private final String room;

    public MockSensorDevice(String id, String name, String room) {
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
        return Optional.of(new Reading(21.5, 45));
    }
}
