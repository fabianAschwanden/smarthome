package fabianaschwanden.smarthome.adapter.out.cover.mock;

import fabianaschwanden.smarthome.domain.model.cover.CoverCommand;
import fabianaschwanden.smarthome.domain.port.out.cover.CoverDevice;
import org.jboss.logging.Logger;

import java.util.OptionalInt;

/**
 * Mock-Store für Entwicklung/Test: hält die Position im Speicher, immer „erreichbar".
 * OPEN → 100, CLOSE → 0, STOP belässt die Position. Wird von
 * {@link MockCoverDeviceFactory} je konfiguriertem Gerät erzeugt.
 */
public class MockCoverDevice implements CoverDevice {

    private static final Logger LOG = Logger.getLogger(MockCoverDevice.class);

    private final String id;
    private final String name;
    private final String room;
    private volatile int position = 100; // Annahme: startet offen

    public MockCoverDevice(String id, String name, String room) {
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
        switch (command) {
            case OPEN -> position = 100;
            case CLOSE -> position = 0;
            case STOP -> { /* Position bleibt */ }
        }
        LOG.infof("[mock] Store '%s' (%s) -> %s (pos %d)", name, id, command, position);
    }

    @Override
    public void setPosition(int position) {
        this.position = position;
        LOG.infof("[mock] Store '%s' (%s) -> Position %d", name, id, position);
    }

    @Override
    public OptionalInt readPosition() {
        return OptionalInt.of(position);
    }
}
