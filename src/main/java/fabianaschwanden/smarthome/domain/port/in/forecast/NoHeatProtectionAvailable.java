package fabianaschwanden.smarthome.domain.port.in.forecast;

/**
 * Es liegt kein Beschattungsfenster vor, das übernommen werden könnte.
 *
 * <p>Eigene Ausnahme statt eines stillen Nichts: Der Aufrufer soll den Unterschied
 * zwischen «nichts zu tun» und «hat nicht geklappt» sehen.
 */
public class NoHeatProtectionAvailable extends RuntimeException {

    public NoHeatProtectionAvailable() {
        super("Derzeit liegt kein Beschattungsfenster vor");
    }
}
