package fabianaschwanden.smarthome.domain.port.in.appliance;

import fabianaschwanden.smarthome.domain.model.appliance.Appliance;
import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;

import java.util.List;

/**
 * Treiber-Port (Use Case): Wellness-Anlagen verwalten – auflisten und eine einzelne
 * Funktion (Pumpe/Heizung/Licht/Massage) schalten.
 */
public interface ControlAppliances {

    List<Appliance> list();

    /**
     * Schaltet eine Funktion einer Anlage.
     *
     * @throws ApplianceNotFound          wenn keine Anlage mit der ID passt.
     * @throws FunctionNotSupported       wenn die Anlage die Funktion nicht hat.
     */
    Appliance switchFunction(String id, ApplianceFunction function, FunctionState state);

    /**
     * Setzt die Soll-Temperatur einer beheizten Anlage (°C).
     *
     * @throws ApplianceNotFound       wenn keine Anlage mit der ID passt.
     * @throws TemperatureNotSupported wenn die Anlage keine Heizung/Temperatur hat.
     * @throws IllegalArgumentException wenn die Temperatur ausserhalb des Sollbereichs liegt.
     */
    Appliance setTargetTemperature(String id, int target);
}
