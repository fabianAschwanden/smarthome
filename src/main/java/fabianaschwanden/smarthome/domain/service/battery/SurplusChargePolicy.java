package fabianaschwanden.smarthome.domain.service.battery;

import fabianaschwanden.smarthome.domain.model.battery.RelayState;

/**
 * Domain-Service: entscheidet im Auto-Modus aus dem PV-Überschuss, ob das
 * Batterie-Relais EIN oder AUS sein soll. Mit Hysterese (zwei Schwellen) gegen
 * ständiges Umschalten bei Werten nahe der Grenze (siehe SPEC §4).
 *
 * <p>Pur und zustandslos: der aktuelle Zustand wird hereingereicht und im
 * Hysterese-Band beibehalten.
 */
public final class SurplusChargePolicy {

    private final double chargeOnWatt;
    private final double chargeOffWatt;

    public SurplusChargePolicy(double chargeOnWatt, double chargeOffWatt) {
        if (chargeOffWatt > chargeOnWatt) {
            throw new IllegalArgumentException(
                    "chargeOffWatt (" + chargeOffWatt + ") darf nicht über chargeOnWatt ("
                            + chargeOnWatt + ") liegen");
        }
        this.chargeOnWatt = chargeOnWatt;
        this.chargeOffWatt = chargeOffWatt;
    }

    /**
     * @param surplusWatt PV-Überschuss (Einspeisung) in Watt; {@code = -gridWatt}.
     * @param current     bisheriger Relais-Zustand (für die Hysterese).
     * @return der gewünschte Relais-Zustand.
     */
    public RelayState decide(double surplusWatt, RelayState current) {
        if (surplusWatt >= chargeOnWatt) {
            return RelayState.ON;
        }
        if (surplusWatt <= chargeOffWatt) {
            return RelayState.OFF;
        }
        return current;
    }
}
