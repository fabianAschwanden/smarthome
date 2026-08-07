package fabianaschwanden.smarthome.domain.model.forecast;

/** Wie der letzte automatische Lauf ausging. */
public enum AutoApplyOutcome {

    /** Empfehlung wurde als Zeitplan übernommen. */
    APPLIED,

    /** Es lag keine Empfehlung vor – an einem trüben Tag der Normalfall. */
    NO_RECOMMENDATION,

    /** Die Prognose lag zuletzt zu oft daneben, um ihr das Schalten zu überlassen. */
    FORECAST_UNRELIABLE,

    /** Noch zu wenige ausgewertete Tage, um über die Verlässlichkeit zu urteilen. */
    NOT_ENOUGH_DATA
}
