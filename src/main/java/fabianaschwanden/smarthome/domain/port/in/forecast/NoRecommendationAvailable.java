package fabianaschwanden.smarthome.domain.port.in.forecast;

/**
 * Es gibt gerade keine Ladeempfehlung – etwa weil die Prognose noch nicht gerechnet ist
 * oder kein Fenster die Schwellen erreicht (z. B. an einem trüben Tag).
 *
 * <p>Bewusst ein Fachfehler und kein leeres Ergebnis: Wer „übernehmen" drückt, erwartet
 * einen Zeitplan; ein stilles Nichts wäre für den Aufrufer nicht unterscheidbar von
 * Erfolg. Der REST-Adapter bildet das auf 409 ab (SPEC §4).
 */
public class NoRecommendationAvailable extends RuntimeException {

    public NoRecommendationAvailable() {
        super("Derzeit liegt keine Ladeempfehlung vor");
    }
}
