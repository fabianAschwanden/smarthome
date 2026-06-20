package fabianaschwanden.smarthome.domain.model.energy;

/**
 * Messquelle für Energiewerte. Bewusst Teil der Domäne: die Fachlogik
 * unterscheidet die Quellen, der Vergleich zweier Quellen ist Domänenwissen.
 */
public enum PowerSource {
    FRONIUS,
    SMARTFOX
}
