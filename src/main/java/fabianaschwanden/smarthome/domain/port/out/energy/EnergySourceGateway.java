package fabianaschwanden.smarthome.domain.port.out.energy;

import fabianaschwanden.smarthome.domain.model.energy.PowerReading;
import fabianaschwanden.smarthome.domain.model.energy.PowerSource;

/**
 * Getriebener Port: eine konkrete Energiequelle (Fronius, SMARTFOX, ...).
 * Adapter in {@code adapter/out} implementieren diesen Port. Bei einem Fehler
 * liefert {@link #read()} eine Messung mit Status {@code ERROR} statt einer Exception,
 * damit eine ausgefallene Quelle die andere nicht blockiert.
 */
public interface EnergySourceGateway {

    PowerSource source();

    PowerReading read();
}
