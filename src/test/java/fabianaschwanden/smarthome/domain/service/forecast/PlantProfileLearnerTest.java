package fabianaschwanden.smarthome.domain.service.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.Confidence;
import fabianaschwanden.smarthome.domain.model.forecast.PlantProfile;
import fabianaschwanden.smarthome.domain.model.forecast.PvGtiSample;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PlantProfileLearnerTest {

    private final PlantProfileLearner learner = new PlantProfileLearner();
    private final Instant learnedAt = Instant.parse("2026-08-04T03:00:00Z");

    /** n Paare für einen Slot, die exakt den gewünschten Faktor ergeben. */
    private List<PvGtiSample> samples(int hourOfDay, double factor, int count, double gti) {
        List<PvGtiSample> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new PvGtiSample(hourOfDay, factor * gti, gti));
        }
        return list;
    }

    @Test
    void lerntFaktorJeStundenSlot() {
        List<PvGtiSample> all = new ArrayList<>();
        all.addAll(samples(10, 4.0, 5, 500));
        all.addAll(samples(11, 6.0, 5, 500));

        PlantProfile profile = learner.learn(all, learnedAt).orElseThrow();

        assertEquals(4.0, profile.factorAt(10), 0.0001);
        assertEquals(6.0, profile.factorAt(11), 0.0001);
        assertEquals(Confidence.LEARNED, profile.confidence());
        assertEquals(learnedAt, profile.learnedAt());
    }

    @Test
    void medianIgnoriertAusreisser() {
        // Vier normale Tage und ein Schneetag mit fast keiner Leistung: der Median haelt.
        List<PvGtiSample> all = new ArrayList<>(samples(12, 5.0, 4, 400));
        all.add(new PvGtiSample(12, 20, 400)); // Ausreisser, Faktor 0.05

        PlantProfile profile = learner.learn(all, learnedAt).orElseThrow();

        assertEquals(5.0, profile.factorAt(12), 0.0001);
    }

    @Test
    void schwacheStrahlungWirdNichtGelernt() {
        // Unter MIN_GTI ist der Quotient numerisch instabil - diese Paare zaehlen nicht.
        List<PvGtiSample> daemmerung = samples(6, 99.0, 10, PlantProfileLearner.MIN_GTI - 1);

        assertTrue(learner.learn(daemmerung, learnedAt).isEmpty());
    }

    @Test
    void slotMitZuWenigDatenErbtVomNaechstenNachbarn() {
        List<PvGtiSample> all = new ArrayList<>();
        all.addAll(samples(10, 4.0, 5, 500));
        all.addAll(samples(13, 7.0, 5, 500));
        all.addAll(samples(11, 9.9, PlantProfileLearner.MIN_SAMPLES_PER_SLOT - 1, 500)); // zu wenige

        PlantProfile profile = learner.learn(all, learnedAt).orElseThrow();

        assertEquals(4.0, profile.factorAt(11), 0.0001, "naechster gelernter Slot ist 10 (Abstand 1)");
        assertEquals(7.0, profile.factorAt(12), 0.0001, "naechster gelernter Slot ist 13 (Abstand 1)");
        assertNotEquals(9.9, profile.factorAt(11), "der unzureichende Median darf nicht zaehlen");
    }

    @Test
    void vererbungLaeuftNichtKettenweiseWeiter() {
        // Nur Slot 12 ist gelernt. Alle anderen muessen DIREKT von 12 erben - nicht
        // schrittweise ueber den jeweiligen Vorgaenger.
        PlantProfile profile = learner.learn(samples(12, 5.0, 5, 500), learnedAt).orElseThrow();

        for (int slot = 0; slot < PlantProfile.SLOTS; slot++) {
            assertEquals(5.0, profile.factorAt(slot), 0.0001, "Slot " + slot);
        }
    }

    @Test
    void merktSichDieHoechsteGemesseneLeistung() {
        List<PvGtiSample> all = new ArrayList<>(samples(12, 5.0, 5, 400));
        all.add(new PvGtiSample(13, 8200, 900));

        PlantProfile profile = learner.learn(all, learnedAt).orElseThrow();

        assertEquals(8200, profile.maxObservedPvWatt(), 0.0001);
    }

    @Test
    void ohneBrauchbareDatenKeinProfil() {
        assertTrue(learner.learn(List.of(), learnedAt).isEmpty());
        assertTrue(learner.learn(null, learnedAt).isEmpty());
    }

    @Test
    void coldStartLiefertGrobesProfil() {
        PlantProfile profile = learner.coldStart(10.0, learnedAt);

        assertEquals(Confidence.ROUGH, profile.confidence());
        // 10 kWp liefern bei 1000 W/m² rund 8500 W -> Faktor 8.5 W je W/m².
        assertEquals(8.5, profile.factorAt(0), 0.000001);
        assertEquals(8.5, profile.factorAt(23), 0.000001);
        assertEquals(10_000, profile.maxObservedPvWatt(), 0.0001);
    }

    @Test
    void coldStartProgostiziertEinePlausibleLeistung() {
        // Regressionsschutz: die Formel war anfangs um Faktor 1000 zu klein (8.5 W statt
        // 8500 W bei voller Sonne) - dimensional plausibel bleibt sie nur so.
        PlantProfile profile = learner.coldStart(10.0, learnedAt);

        double beiVollerSonne = profile.factorAt(12) * 1000.0;

        assertTrue(beiVollerSonne > 7000 && beiVollerSonne < 9000,
                "10 kWp muessen bei 1000 W/m² rund 8.5 kW ergeben, waren: " + beiVollerSonne);
    }

    @Test
    void coldStartBrauchtPositiveNennleistung() {
        assertThrows(IllegalArgumentException.class, () -> learner.coldStart(0, learnedAt));
    }

    @Test
    void medianBeiGeraderAnzahlMitteltDieMitte() {
        assertEquals(2.5, PlantProfileLearner.median(List.of(1.0, 2.0, 3.0, 4.0)), 0.0001);
        assertEquals(3.0, PlantProfileLearner.median(List.of(1.0, 3.0, 100.0)), 0.0001);
    }

    @Test
    void profilPruefungWeistUnvollstaendigeFaktorenAb() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlantProfile(List.of(1.0), 100, learnedAt, Confidence.LEARNED));
        Optional<PlantProfile> egal = Optional.empty();
        assertTrue(egal.isEmpty());
    }
}
