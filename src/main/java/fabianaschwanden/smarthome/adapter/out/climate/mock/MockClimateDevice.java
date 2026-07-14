package fabianaschwanden.smarthome.adapter.out.climate.mock;

import fabianaschwanden.smarthome.domain.model.climate.ClimateMode;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateDevice;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Mock-Klimaanlage für Entwicklung/Test: hält den Zustand im Speicher, immer
 * „erreichbar". Liefert eine plausible Ist-Temperatur (21 °C). Wird von
 * {@link MockClimateDeviceFactory} je konfiguriertem Gerät erzeugt. Meldet zusätzlich
 * eine plausible Außentemperatur (14 °C).
 */
public class MockClimateDevice implements ClimateDevice {

    private static final Logger LOG = Logger.getLogger(MockClimateDevice.class);

    private final String id;
    private final String name;
    private final String room;

    private volatile boolean power = false;
    private volatile ClimateMode mode = ClimateMode.AUTO;
    private volatile int targetTemp = 22;
    private volatile int currentTemp = 21;
    private volatile int outdoorTemp = 14;

    public MockClimateDevice(String id, String name, String room) {
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
        this.power = on;
        LOG.infof("[mock] Klima '%s' (%s) -> %s", name, id, on ? "EIN" : "AUS");
    }

    @Override
    public void applyMode(ClimateMode mode) {
        this.mode = mode;
        LOG.infof("[mock] Klima '%s' (%s) -> Modus %s", name, id, mode);
    }

    @Override
    public void applyTargetTemp(int temperature) {
        this.targetTemp = temperature;
        LOG.infof("[mock] Klima '%s' (%s) -> Soll %d°C", name, id, temperature);
    }

    @Override
    public Optional<State> readState() {
        return Optional.of(new State(power, mode, targetTemp, currentTemp, outdoorTemp));
    }
}
