package fabianaschwanden.smarthome.domain.model.forecast;

/**
 * Ein Verbrauchswert für die Baseline: Mittel des Hausverbrauchs einer Stunde, zusammen
 * mit ihrem Stunden-Slot und der Information, ob es ein Wochenendtag war.
 *
 * <p>Die Trennung Werktag/Wochenende steckt bewusst schon im Sample: ob ein Datum ein
 * Wochenende ist, hängt an der Zeitzone – die Entscheidung fällt beim Einlesen, damit die
 * Domäne zeitzonenfrei rechnen kann (SPEC §3.3).
 */
public record ConsumptionSample(int hourOfDay, boolean weekend, double consumptionWatt) {

    public ConsumptionSample {
        if (hourOfDay < 0 || hourOfDay > 23) {
            throw new IllegalArgumentException("hourOfDay muss zwischen 0 und 23 liegen: " + hourOfDay);
        }
        if (consumptionWatt < 0) {
            throw new IllegalArgumentException("consumptionWatt darf nicht negativ sein: " + consumptionWatt);
        }
    }
}
