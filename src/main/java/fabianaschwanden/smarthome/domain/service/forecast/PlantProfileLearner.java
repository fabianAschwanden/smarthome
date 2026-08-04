package fabianaschwanden.smarthome.domain.service.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.Confidence;
import fabianaschwanden.smarthome.domain.model.forecast.PlantProfile;
import fabianaschwanden.smarthome.domain.model.forecast.PvGtiSample;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Lernt aus gemessenen Paaren (Strahlung ↔ tatsächliche Leistung), wie die eigene Anlage
 * Strahlung in Leistung umsetzt. Reiner Domain-Service: zustandslos, framework-frei, alle
 * Zeitpunkte kommen als Parameter herein.
 *
 * <p>Der Ansatz ersetzt die Konfiguration von kWp und Wirkungsgrad: Statt Datenblattwerte
 * zu pflegen, misst die App an der eigenen Historie, was tatsächlich herauskommt – inklusive
 * Verschattung, Modulalterung und Verschmutzung (SPEC §3.1).
 */
public final class PlantProfileLearner {

    /**
     * Unterhalb dieser Strahlung wird nicht gelernt. In der Dämmerung ist der Quotient
     * {@code pvWatt / gti} numerisch instabil: kleine Messfehler im Nenner erzeugen
     * riesige Faktoren, die den Median eines Slots verzerren würden.
     */
    public static final double MIN_GTI = 50.0;

    /** So viele Messpunkte braucht ein Slot, damit sein eigener Median zählt. */
    public static final int MIN_SAMPLES_PER_SLOT = 5;

    /** Cold-Start-Annahme: Wirkungsgrad von der Nennleistung bis zur Einspeisung. */
    private static final double COLD_START_EFFICIENCY = 0.85;

    /** Nennleistung in kW → W, damit der Faktor die Einheit W pro W/m² bekommt. */
    private static final double KW_TO_W = 1000.0;

    /**
     * Lernt ein Profil. Liefert {@code empty}, wenn kein einziger Slot genug brauchbare
     * Paare hat – dann bleibt nur {@link #coldStart(double, Instant)}.
     */
    public Optional<PlantProfile> learn(List<PvGtiSample> samples, Instant learnedAt) {
        if (samples == null || samples.isEmpty()) {
            return Optional.empty();
        }
        List<List<Double>> quotientsPerSlot = new ArrayList<>();
        for (int slot = 0; slot < PlantProfile.SLOTS; slot++) {
            quotientsPerSlot.add(new ArrayList<>());
        }
        double maxPvWatt = 0;
        for (PvGtiSample sample : samples) {
            maxPvWatt = Math.max(maxPvWatt, sample.pvWatt());
            if (sample.gtiWattPerSqm() >= MIN_GTI) {
                quotientsPerSlot.get(sample.hourOfDay()).add(sample.pvWatt() / sample.gtiWattPerSqm());
            }
        }

        // Median statt Mittelwert: ein einzelner Schneetag oder eine Abregelung soll den
        // Slot nicht dauerhaft nach unten ziehen.
        Double[] factors = new Double[PlantProfile.SLOTS];
        boolean anyLearned = false;
        for (int slot = 0; slot < PlantProfile.SLOTS; slot++) {
            List<Double> quotients = quotientsPerSlot.get(slot);
            if (quotients.size() >= MIN_SAMPLES_PER_SLOT) {
                factors[slot] = median(quotients);
                anyLearned = true;
            }
        }
        if (!anyLearned) {
            return Optional.empty();
        }

        inheritFromNearestNeighbour(factors);
        return Optional.of(new PlantProfile(List.of(factors), maxPvWatt, learnedAt, Confidence.LEARNED));
    }

    /**
     * Fallback ohne Historie: ein pauschaler Faktor aus der konfigurierten Nennleistung.
     * Die Prognose ist dadurch grob – deshalb {@link Confidence#ROUGH}, damit das UI sie
     * entsprechend kennzeichnet.
     */
    public PlantProfile coldStart(double kwp, Instant learnedAt) {
        if (kwp <= 0) {
            throw new IllegalArgumentException("kwp muss positiv sein: " + kwp);
        }
        double factor = kwp * COLD_START_EFFICIENCY / KW_TO_W;
        return new PlantProfile(
                Collections.nCopies(PlantProfile.SLOTS, factor),
                kwp * KW_TO_W,
                learnedAt,
                Confidence.ROUGH);
    }

    /**
     * Slots ohne genügend eigene Daten erben den nächstgelegenen gelernten Slot. Das
     * betrifft vor allem Nachtstunden – dort ist die Strahlung ohnehin 0, der geerbte
     * Faktor wirkt sich also nicht aus – und Randstunden mit wenig Sonne.
     */
    private void inheritFromNearestNeighbour(Double[] factors) {
        // Gegen den Original-Stand suchen, nicht gegen das Array, das gerade befüllt wird:
        // sonst würde ein soeben geerbter Wert selbst zur Quelle und der Faktor eines
        // einzelnen Slots liefe kettenweise über den halben Tag.
        Double[] learned = factors.clone();
        for (int slot = 0; slot < factors.length; slot++) {
            if (learned[slot] != null) {
                continue;
            }
            Double nearest = null;
            for (int distance = 1; distance < learned.length && nearest == null; distance++) {
                int before = slot - distance;
                int after = slot + distance;
                if (before >= 0 && learned[before] != null) {
                    nearest = learned[before];
                } else if (after < learned.length && learned[after] != null) {
                    nearest = learned[after];
                }
            }
            factors[slot] = nearest;
        }
    }

    /** Median einer Werteliste; die Liste wird dafür kopiert und sortiert. */
    static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        int middle = size / 2;
        return size % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }
}
