package fabianaschwanden.smarthome.adapter.out.energy.mock;

import fabianaschwanden.smarthome.domain.model.energy.PowerReading;
import fabianaschwanden.smarthome.domain.model.energy.PowerSource;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySourceGateway;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

/**
 * Synthetische SMARTFOX-Quelle für Entwicklung/Test (aktiv, solange nicht gegen echte
 * Geräte gefahren wird). Liest den selben PV-/Verbrauchsverlauf wie der Fronius-Mock,
 * jedoch leicht zeitversetzt/gerundet – wie in der Realität, wo der SMARTFOX den Fronius
 * via Modbus weiterreicht (siehe SPEC §5).
 */
@ApplicationScoped
@UnlessBuildProperty(name = "smarthome.real-devices", stringValue = "true", enableIfMissing = true)
public class MockSmartfoxSource implements EnergySourceGateway {

    @Override
    public PowerSource source() {
        return PowerSource.SMARTFOX;
    }

    @Override
    public PowerReading read() {
        double pv = SyntheticGrid.pvWatt();
        double consumption = SyntheticGrid.consumptionWatt();
        // SMARTFOX rundet die PV-Zahl (ganze Watt) und liegt minimal tiefer.
        double pvSmartfox = Math.round(pv);
        double grid = consumption - pvSmartfox;
        return PowerReading.of(PowerSource.SMARTFOX, Instant.now(), grid, pvSmartfox, null, consumption);
    }
}
