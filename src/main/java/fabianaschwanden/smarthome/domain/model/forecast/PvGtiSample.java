package fabianaschwanden.smarthome.domain.model.forecast;

/**
 * Ein Lern-Paar: was die Anlage in einer Stunde geliefert hat ({@code pvWatt}, Mittel
 * über die Stunde) gegenüber der Ist-Strahlung in Modulebene ({@code gtiWattPerSqm}).
 *
 * <p>{@code hourOfDay} ist der Stunden-Slot 0–23 in lokaler Zeit – der Slot trägt die
 * Verschattung (Bäume, Kamin, Horizont), die ein globaler Faktor nicht abbilden könnte
 * (SPEC §3.1).
 */
public record PvGtiSample(int hourOfDay, double pvWatt, double gtiWattPerSqm) {

    public PvGtiSample {
        if (hourOfDay < 0 || hourOfDay > 23) {
            throw new IllegalArgumentException("hourOfDay muss zwischen 0 und 23 liegen: " + hourOfDay);
        }
        if (pvWatt < 0) {
            throw new IllegalArgumentException("pvWatt darf nicht negativ sein: " + pvWatt);
        }
        if (gtiWattPerSqm < 0) {
            throw new IllegalArgumentException("gtiWattPerSqm darf nicht negativ sein: " + gtiWattPerSqm);
        }
    }
}
