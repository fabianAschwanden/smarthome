package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.model.forecast.Confidence;
import fabianaschwanden.smarthome.domain.model.forecast.PlantProfile;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistenz des Anlagenprofils gegen die Dev-Services-Postgres. Prüft vor allem, dass
 * das Profil einen Neustart übersteht (Runde: speichern → laden → gleich) und dass neu
 * Lernen die eine Zeile überschreibt statt eine zweite anzulegen.
 */
@QuarkusTest
class PanachePlantProfileRepositoryTest {

    @Inject
    PanachePlantProfileRepository repository;

    private final Instant learnedAt = Instant.parse("2026-08-04T03:00:00Z");

    private PlantProfile profile(double factorBase, double maxWatt, Confidence confidence, Instant at) {
        List<Double> factors = new ArrayList<>();
        for (int slot = 0; slot < PlantProfile.SLOTS; slot++) {
            factors.add(factorBase + slot * 0.01);
        }
        return new PlantProfile(factors, maxWatt, at, confidence);
    }

    @BeforeEach
    @Transactional
    void leeren() {
        repository.deleteAll();
    }

    @Test
    void ohneGelerntesProfilLeer() {
        assertTrue(repository.load().isEmpty());
    }

    @Test
    void speichernUndLadenErgibtDasselbeProfil() {
        PlantProfile original = profile(4.0, 8200, Confidence.LEARNED, learnedAt);

        repository.save(original);
        PlantProfile geladen = repository.load().orElseThrow();

        assertEquals(original.factorPerHour(), geladen.factorPerHour());
        assertEquals(original.maxObservedPvWatt(), geladen.maxObservedPvWatt(), 0.0001);
        assertEquals(original.learnedAt(), geladen.learnedAt());
        assertEquals(original.confidence(), geladen.confidence());
        assertEquals(original, geladen, "record-Gleichheit ueber alle Felder");
    }

    @Test
    void alleVierundzwanzigFaktorenUeberlebenDieRunde() {
        PlantProfile original = profile(1.5, 5000, Confidence.LEARNED, learnedAt);

        repository.save(original);
        PlantProfile geladen = repository.load().orElseThrow();

        assertEquals(PlantProfile.SLOTS, geladen.factorPerHour().size());
        for (int slot = 0; slot < PlantProfile.SLOTS; slot++) {
            assertEquals(original.factorAt(slot), geladen.factorAt(slot), 0.000001, "Slot " + slot);
        }
    }

    @Test
    void neuLernenUeberschreibtStattEineZweiteZeileAnzulegen() {
        repository.save(profile(4.0, 8200, Confidence.ROUGH, learnedAt));
        Instant spaeter = learnedAt.plusSeconds(86_400);

        repository.save(profile(6.0, 9100, Confidence.LEARNED, spaeter));

        assertEquals(1, repository.count(), "es darf genau eine Zeile geben");
        PlantProfile geladen = repository.load().orElseThrow();
        assertEquals(6.0, geladen.factorAt(0), 0.000001);
        assertEquals(9100, geladen.maxObservedPvWatt(), 0.0001);
        assertEquals(Confidence.LEARNED, geladen.confidence());
        assertEquals(spaeter, geladen.learnedAt());
    }

    @Test
    void coldStartProfilUeberlebtMitSeinerConfidence() {
        // ROUGH muss erhalten bleiben, sonst zeigt das UI eine grobe Prognose als
        // belastbar an.
        repository.save(profile(0.0085, 10_000, Confidence.ROUGH, learnedAt));

        assertEquals(Confidence.ROUGH, repository.load().orElseThrow().confidence());
    }

    @Test
    void nullProfilWirdAbgewiesen() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
    }
}
