package fabianaschwanden.smarthome.domain.port.in.appliance;

import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;

/** Die Anlage besitzt die angefragte Funktion nicht (REST: 400). */
public class FunctionNotSupported extends RuntimeException {

    public FunctionNotSupported(String id, ApplianceFunction function) {
        super("Anlage '" + id + "' hat keine Funktion " + function);
    }
}
