package fabianaschwanden.smarthome.domain.model.forecast;

/**
 * Die Empfehlung, in einem bestimmten Fenster zu laden – das beste Überschussfenster des
 * Tages, versehen mit der Belastbarkeit der zugrunde liegenden Prognose.
 *
 * <p>Bewusst nur eine Empfehlung: Ausgeführt wird sie erst, wenn der Nutzer sie als
 * Batterie-Zeitplan übernimmt (UC 14). Dieser Use Case regelt nicht selbst – die
 * Überschuss-Regelung in Echtzeit bleibt beim SMARTFOX (SPEC §1).
 */
public record ChargeRecommendation(SurplusWindow window, Confidence confidence) {

    public ChargeRecommendation {
        if (window == null) {
            throw new IllegalArgumentException("window darf nicht null sein");
        }
        if (confidence == null) {
            throw new IllegalArgumentException("confidence darf nicht null sein");
        }
    }
}
