package fabianaschwanden.smarthome.domain.port.in.climate;

/**
 * Die Klimaanlage ist bewusst stillgelegt (über den Winter vom Strom) und nimmt keine
 * Befehle an. Kein Fehler des Geräts – ein Zustand, den jemand gewählt hat.
 */
public class ClimateDeactivated extends RuntimeException {

    private final String climateId;

    public ClimateDeactivated(String climateId) {
        super("Klimaanlage '" + climateId + "' ist stillgelegt");
        this.climateId = climateId;
    }

    public String climateId() {
        return climateId;
    }
}
