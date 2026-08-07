package fabianaschwanden.smarthome.domain.port.in.forecast;

/**
 * Es steht eine Wellness-Heizung an; erzwungenes Laden würde sich mit ihr überschneiden.
 *
 * <p>Eigene Ausnahme statt eines stillen Nichts: Der Aufrufer soll den Unterschied
 * zwischen «keine Empfehlung» und «bewusst nicht, weil der Whirlpool heizt» sehen.
 */
public class ChargingBlockedByWellness extends RuntimeException {

    public ChargingBlockedByWellness() {
        super("Der Whirlpool heizt im Überschussfenster – die Batterie lädt derweil über den Automatik-Modus");
    }
}
