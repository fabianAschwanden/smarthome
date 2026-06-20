package fabianaschwanden.smarthome.adapter.out.energy.mock;

import fabianaschwanden.smarthome.domain.model.energy.PowerReading;
import fabianaschwanden.smarthome.domain.model.energy.PowerSource;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySourceGateway;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

/**
 * Synthetische Fronius-Quelle für Entwicklung/Test (aktiv, solange nicht gegen echte
 * Geräte gefahren wird). Erzeugt einen tagesähnlichen PV-Verlauf und leicht abweichende
 * Werte gegenüber dem SMARTFOX-Mock, damit die Differenz-Anzeige im Dashboard sichtbar wird.
 */
@ApplicationScoped
@UnlessBuildProperty(name = "smarthome.real-devices", stringValue = "true", enableIfMissing = true)
public class MockFroniusSource implements EnergySourceGateway {

    @Override
    public PowerSource source() {
        return PowerSource.FRONIUS;
    }

    @Override
    public PowerReading read() {
        double pv = SyntheticGrid.pvWatt();
        double consumption = SyntheticGrid.consumptionWatt();
        // Fronius misst PV minimal höher (eigener Messpfad) -> erklärt sichtbare Differenz.
        double pvFronius = pv * 1.01;
        double grid = consumption - pvFronius;
        // Plausible Tages-/Relativwerte für die Dashboard-Anzeige (Mock).
        double selfConsumption = pvFronius > 0 ? Math.min(100, consumption / pvFronius * 100) : 0;
        double autonomy = consumption > 0 ? Math.min(100, Math.max(0, pvFronius / consumption * 100)) : 100;
        PowerReading.DailyEnergy daily = new PowerReading.DailyEnergy(
                9_800.0, 32_434_721.0, autonomy, selfConsumption);
        return PowerReading.of(PowerSource.FRONIUS, Instant.now(), grid, pvFronius, null, consumption, daily);
    }
}
