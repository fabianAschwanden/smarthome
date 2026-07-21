package fabianaschwanden.smarthome.adapter.out.battery.mock;

import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.battery.RelayReading;
import fabianaschwanden.smarthome.domain.port.out.battery.RelaySwitch;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Mock-Relais für Entwicklung/Test (aktiv, solange nicht gegen echte Geräte gefahren
 * wird): merkt sich Modus und Zustand im Speicher, ohne Hardware anzusprechen.
 */
@ApplicationScoped
@UnlessBuildProperty(name = "smarthome.real-devices", stringValue = "true", enableIfMissing = true)
public class MockRelaySwitch implements RelaySwitch {

    private static final Logger LOG = Logger.getLogger(MockRelaySwitch.class);

    private volatile ControlMode mode = ControlMode.MANUAL;
    private volatile RelayState state = RelayState.OFF;

    @Override
    public void apply(ControlMode mode, RelayState state) {
        this.mode = mode;
        this.state = state;
        LOG.infof("[mock] Relais gestellt: Modus=%s Zustand=%s", mode, state);
    }

    @Override
    public Optional<RelayReading> read() {
        return Optional.of(new RelayReading(mode, state));
    }
}
