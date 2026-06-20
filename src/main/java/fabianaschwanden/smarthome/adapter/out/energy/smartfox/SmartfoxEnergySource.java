package fabianaschwanden.smarthome.adapter.out.energy.smartfox;

import fabianaschwanden.smarthome.domain.model.energy.PowerReading;
import fabianaschwanden.smarthome.domain.model.energy.PowerSource;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySourceGateway;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Liest den SMARTFOX über {@code GET <base-url>/values.xml}. Die XML-Antwort
 * enthält viele {@code <value id="...">Text</value>}-Einträge (Werte teils mit
 * Einheit). Feld-/Vorzeichen-Mapping siehe SPEC §3.2/§4; gegen die reale,
 * firmware-abhängige Anlage zu verifizieren (SPEC §11).
 *
 * <p>Aktiv, wenn {@code smarthome.real-devices=true} (Profile {@code %prod}/{@code %live}),
 * sonst übernimmt der Mock.
 */
@ApplicationScoped
@IfBuildProperty(name = "smarthome.real-devices", stringValue = "true")
public class SmartfoxEnergySource implements EnergySourceGateway {

    private static final Logger LOG = Logger.getLogger(SmartfoxEnergySource.class);

    private final String valuesUrl;

    public SmartfoxEnergySource(@ConfigProperty(name = "energy.smartfox.base-url") String baseUrl) {
        this.valuesUrl = baseUrl + "/values.xml";
    }

    @Override
    public PowerSource source() {
        return PowerSource.SMARTFOX;
    }

    @Override
    public PowerReading read() {
        try {
            SmartfoxValues values = SmartfoxValues.fetch(valuesUrl);
            double pv = values.watt("hidProduction").orElse(0.0);
            // detailsPowerValue ist der Netzwert am Messpunkt und folgt bereits der
            // normalisierten Konvention (+ = Bezug, − = Einspeisung). pUserValue ist
            // an dieser Anlage 0 und unbrauchbar -> Verbrauch aus der Energiebilanz:
            //   Verbrauch = PV + Netz   (Einspeisung mindert, Bezug erhöht).
            double grid = values.watt("detailsPowerValue").orElse(0.0);
            double battery = values.watt("hidBatteryPower").orElse(0.0);
            double consumption = pv + grid - battery;
            Double batteryWatt = values.watt("hidBatteryPower").isPresent() ? battery : null;
            return PowerReading.of(PowerSource.SMARTFOX, Instant.now(), grid, pv, batteryWatt, consumption);
        } catch (Exception e) {
            LOG.warnf("SMARTFOX nicht lesbar: %s", e.getMessage());
            return PowerReading.error(PowerSource.SMARTFOX, Instant.now());
        }
    }
}
