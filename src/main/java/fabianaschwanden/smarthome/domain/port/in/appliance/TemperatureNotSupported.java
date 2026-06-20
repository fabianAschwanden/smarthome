package fabianaschwanden.smarthome.domain.port.in.appliance;

/** Die Anlage hat keine Heizung/Temperatur-Steuerung (REST: 400). */
public class TemperatureNotSupported extends RuntimeException {

    public TemperatureNotSupported(String id) {
        super("Anlage '" + id + "' hat keine Temperatur-Steuerung");
    }
}
