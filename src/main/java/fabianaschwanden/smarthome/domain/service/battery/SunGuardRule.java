package fabianaschwanden.smarthome.domain.service.battery;

import fabianaschwanden.smarthome.domain.model.battery.SunGuard;
import fabianaschwanden.smarthome.domain.model.battery.SunGuardAction;

import java.util.OptionalDouble;

/**
 * Die Entscheidung des Ohne-Sonne-Ausschalters – reine Fachlogik, ohne Uhr, Datenbank
 * und Relais.
 *
 * <p><b>Zwei Schwellen statt einer.</b> Mit nur einer Schwelle stünde der Wächter an
 * jedem trüben Nachmittag im Grenzbereich und schaltete im Minutentakt scharf und
 * stumpf. Scharf wird er erst deutlich oberhalb der Abschaltschwelle.
 *
 * <p><b>Ohne Messwerte passiert nichts.</b> Eine tote Energiequelle sieht aus wie eine
 * Nacht. Ein Wächter, der bei Messausfall abschaltet, wäre schlimmer als keiner.
 *
 * @param sunWatt  ab dieser PV-Leistung gilt die Sonne als da (scharf stellen)
 * @param darkWatt bis zu dieser PV-Leistung gilt sie als weg (abschalten)
 */
public record SunGuardRule(double sunWatt, double darkWatt) {

    public SunGuardRule {
        if (darkWatt < 0) {
            throw new IllegalArgumentException("darkWatt darf nicht negativ sein");
        }
        if (sunWatt <= darkWatt) {
            throw new IllegalArgumentException(
                    "sunWatt muss über darkWatt liegen, sonst flattert der Wächter");
        }
    }

    /**
     * @param guard         aktueller Stand des Wächters
     * @param medianPvWatt  mittlere PV-Leistung im Beobachtungsfenster; leer ohne Messpunkte
     */
    public SunGuardAction decide(SunGuard guard, OptionalDouble medianPvWatt) {
        if (guard == null) {
            throw new IllegalArgumentException("guard darf nicht null sein");
        }
        if (!guard.enabled() || medianPvWatt.isEmpty()) {
            return SunGuardAction.NOTHING;
        }
        double pv = medianPvWatt.getAsDouble();
        if (!guard.armed()) {
            return pv >= sunWatt ? SunGuardAction.ARM : SunGuardAction.NOTHING;
        }
        return pv <= darkWatt ? SunGuardAction.TRIP : SunGuardAction.NOTHING;
    }
}
