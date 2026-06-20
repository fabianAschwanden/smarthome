package fabianaschwanden.smarthome.adapter.out.tuya.mock;

import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import fabianaschwanden.smarthome.domain.port.out.tuya.SwitchDevice;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Mock-Schaltgerät für Entwicklung/Test: hält den Zustand im Speicher, immer
 * „erreichbar", ohne Hardware. Wird von {@link MockSwitchDeviceFactory} je
 * konfiguriertem Gerät erzeugt.
 */
public class MockSwitchDevice implements SwitchDevice {

    private static final Logger LOG = Logger.getLogger(MockSwitchDevice.class);

    private final String id;
    private final String name;
    private final String room;
    private final boolean critical;
    private volatile SwitchState state = SwitchState.OFF;

    public MockSwitchDevice(String id, String name, String room, boolean critical) {
        this.id = id;
        this.name = name;
        this.room = room;
        this.critical = critical;
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
        return critical;
    }

    @Override
    public void apply(SwitchState state) {
        this.state = state;
        LOG.infof("[mock] Tuya '%s' (%s) -> %s", name, id, state);
    }

    @Override
    public Optional<SwitchState> readState() {
        return Optional.of(state);
    }
}
