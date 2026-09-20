package fabianaschwanden.smarthome.domain.port.in.appliance;

/**
 * Die Anlage ist bewusst stillgelegt (z. B. über den Winter vom Strom) und nimmt
 * keine Befehle an. Kein Fehler des Geräts – ein Zustand, den jemand gewählt hat.
 */
public class ApplianceDeactivated extends RuntimeException {

    private final String applianceId;

    public ApplianceDeactivated(String applianceId) {
        super("Anlage '" + applianceId + "' ist deaktiviert");
        this.applianceId = applianceId;
    }

    public String applianceId() {
        return applianceId;
    }
}
