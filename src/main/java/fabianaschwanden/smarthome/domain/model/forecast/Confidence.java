package fabianaschwanden.smarthome.domain.model.forecast;

/**
 * Wie belastbar eine Prognose ist.
 *
 * <p>{@link #LEARNED} steht für ein aus der eigenen Historie gelerntes Anlagenprofil,
 * {@link #ROUGH} für den Cold-Start-Fallback aus der konfigurierten kWp-Angabe. Das UI
 * kennzeichnet ROUGH als „grob", damit niemand einer Zahl vertraut, die nur auf einer
 * Faustformel beruht (SPEC §3.1).
 */
public enum Confidence {

    /** Faktoren stammen aus gemessenen GTI-/PV-Paaren der eigenen Anlage. */
    LEARNED,

    /** Cold Start: pauschaler Faktor aus {@code forecast.plant.kwp}. */
    ROUGH
}
