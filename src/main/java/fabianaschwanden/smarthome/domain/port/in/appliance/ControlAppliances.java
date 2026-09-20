package fabianaschwanden.smarthome.domain.port.in.appliance;

import fabianaschwanden.smarthome.domain.model.appliance.Appliance;
import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;

import java.util.List;
import java.util.OptionalInt;

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

    /**
     * Die gewünschte Soll-Temperatur, solange die Anlage sie noch nicht übernommen hat –
     * sonst leer.
     *
     * <p>Der Gecko-Befehl wirkt verzögert: Unmittelbar nach dem Setzen meldet die Anlage
     * noch den alten Wert. Ohne diese Auskunft zeigte die Oberfläche den alten Wert an,
     * und der nächste Schritt rechnete wieder von dort – man käme nie mehr als ein Grad
     * weit.
     */
    OptionalInt pendingTarget(String id);

    /**
     * Legt eine Anlage still oder nimmt sie wieder in Betrieb.
     *
     * <p>Stillgelegt heisst: kein Gerätezugriff mehr, keine Befehle (409), keine
     * Zeitsteuerung, kein Überschussplan – bis jemand sie wieder aktiviert. Gedacht
     * für Anlagen, die über den Winter vom Strom sind.
     *
     * @throws ApplianceNotFound wenn keine Anlage mit der ID passt.
     */
    Appliance setActive(String id, boolean active);

    /**
     * Ob eine Anlage in Betrieb ist – ohne das Gerät anzusprechen. Für Dienste, die
     * vor dem Schalten fragen wollen, ob sich das überhaupt lohnt.
     *
     * @throws ApplianceNotFound wenn keine Anlage mit der ID passt.
     */
    boolean isActive(String id);
}
