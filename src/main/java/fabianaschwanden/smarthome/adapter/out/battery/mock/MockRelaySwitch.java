package fabianaschwanden.smarthome.adapter.out.battery.mock;

import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.port.out.battery.RelaySwitch;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Mock-Relais für Entwicklung/Test (aktiv, solange nicht gegen echte Geräte gefahren
 * wird): merkt sich den zuletzt geschalteten Zustand im Speicher, ohne Hardware anzusprechen.
 */
@ApplicationScoped
@UnlessBuildProperty(name = "smarthome.real-devices", stringValue = "true", enableIfMissing = true)
public class MockRelaySwitch implements RelaySwitch {

    private static final Logger LOG = Logger.getLogger(MockRelaySwitch.class);

    private volatile RelayState state = RelayState.OFF;

    @Override
    public void apply(RelayState state) {
        this.state = state;
        LOG.infof("[mock] Relais geschaltet: %s", state);
    }

    @Override
    public Optional<RelayState> read() {
        return Optional.of(state);
    }

    /** Letzter geschalteter Zustand – für Tests/Diagnose. */
    public RelayState state() {
        return state;
    }
}
