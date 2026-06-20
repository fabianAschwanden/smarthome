package fabianaschwanden.smarthome.adapter.out.safety.mock;

import fabianaschwanden.smarthome.domain.model.safety.AlarmState;
import fabianaschwanden.smarthome.domain.port.out.safety.SmokeDetectorDevice;

import java.util.Optional;

/** Mock-Rauchmelder für Entwicklung/Test: kein Alarm, voller Akku. */
public class MockSmokeDetectorDevice implements SmokeDetectorDevice {

    private final String id;
    private final String name;
    private final String room;

    public MockSmokeDetectorDevice(String id, String name, String room) {
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
        return Optional.of(new Reading(AlarmState.OK, 100));
    }
}
