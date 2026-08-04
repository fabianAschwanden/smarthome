package fabianaschwanden.smarthome.domain.service.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.ChargeRecommendation;
import fabianaschwanden.smarthome.domain.model.forecast.Confidence;
import fabianaschwanden.smarthome.domain.model.forecast.ConsumptionBaseline;
import fabianaschwanden.smarthome.domain.model.forecast.ConsumptionSample;
import fabianaschwanden.smarthome.domain.model.forecast.PvForecast;
import fabianaschwanden.smarthome.domain.model.forecast.SurplusWindow;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SurplusPlannerTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final Duration TWO_HOURS = Duration.ofHours(2);

    private final SurplusPlanner planner = new SurplusPlanner();
    private final Instant learnedAt = Instant.parse("2026-08-04T03:00:00Z");
    private final Instant computedAt = Instant.parse("2026-08-04T04:00:00Z");

    private ConsumptionBaseline flatBaseline(double watt) {
        List<Double> slots = Collections.nCopies(ConsumptionBaseline.SLOTS, watt);
        return new ConsumptionBaseline(slots, slots);
    }

    /** Prognose ab 08:00Z (10:00 lokal) mit den angegebenen Stundenleistungen. */
    private PvForecast forecastWith(double... watts) {
        List<PvForecast.HourEntry> hours = new ArrayList<>();
        Instant start = Instant.parse("2026-08-04T08:00:00Z");
        for (int i = 0; i < watts.length; i++) {
            hours.add(new PvForecast.HourEntry(start.plusSeconds(i * 3600L), watts[i], 0));
        }
        return new PvForecast(hours, 0, 0, Confidence.LEARNED, learnedAt, computedAt);
    }

    @Test
    void bildetFensterAusZusammenhaengendenStunden() {
        // Baseline 500 W; Ueberschuss = Leistung - 500, Schwelle 500 W.
        PvForecast forecast = forecastWith(600, 1500, 2000, 1800, 600);

        List<SurplusWindow> windows = planner.windows(forecast, flatBaseline(500), ZURICH, 500, TWO_HOURS);

        assertEquals(1, windows.size());
        SurplusWindow window = windows.get(0);
        assertEquals(Instant.parse("2026-08-04T09:00:00Z"), window.from());
        assertEquals(Instant.parse("2026-08-04T12:00:00Z"), window.to(), "to ist exklusiv");
        assertEquals(Duration.ofHours(3), window.duration());
        assertEquals(1500.0, window.peakSurplusWatt(), 0.0001);
        assertEquals((1000 + 1500 + 1300) / 1000.0, window.expectedKwh(), 0.0001);
    }

    @Test
    void zuKurzesFensterFaelltRaus() {
        PvForecast forecast = forecastWith(600, 2000, 600);

        assertTrue(planner.windows(forecast, flatBaseline(500), ZURICH, 500, TWO_HOURS).isEmpty());
    }

    @Test
    void unterDerSchwelleEntstehtKeinFenster() {
        PvForecast forecast = forecastWith(900, 900, 900, 900);

        assertTrue(planner.windows(forecast, flatBaseline(500), ZURICH, 500, TWO_HOURS).isEmpty());
    }

    @Test
    void mehrereFensterUndDasBesteGewinnt() {
        // Zwei Fenster: 2 h schwach, dann Pause, dann 3 h stark.
        PvForecast forecast = forecastWith(1200, 1200, 400, 3000, 3000, 3000);

        List<SurplusWindow> windows = planner.windows(forecast, flatBaseline(500), ZURICH, 500, TWO_HOURS);

        assertEquals(2, windows.size());
        SurplusWindow best = planner.best(windows).orElseThrow();
        assertEquals(Instant.parse("2026-08-04T11:00:00Z"), best.from());
        assertEquals(7.5, best.expectedKwh(), 0.0001);
    }

    @Test
    void empfehlungTraegtDieConfidenceDerPrognose() {
        PvForecast rough = new PvForecast(
                forecastWith(2000, 2000, 2000).hours(), 0, 0, Confidence.ROUGH, learnedAt, computedAt);

        ChargeRecommendation recommendation =
                planner.recommend(rough, flatBaseline(500), ZURICH, 500, TWO_HOURS).orElseThrow();

        assertEquals(Confidence.ROUGH, recommendation.confidence());
        assertEquals(Duration.ofHours(3), recommendation.window().duration());
    }

    @Test
    void ohneFensterKeineEmpfehlung() {
        PvForecast forecast = forecastWith(100, 100);

        assertEquals(Optional.empty(), planner.recommend(forecast, flatBaseline(500), ZURICH, 500, TWO_HOURS));
        assertEquals(Optional.empty(), planner.best(List.of()));
        assertEquals(Optional.empty(), planner.best(null));
    }

    @Test
    void baselineTrenntWerktagUndWochenende() {
        List<ConsumptionSample> samples = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            samples.add(new ConsumptionSample(12, false, 400));
            samples.add(new ConsumptionSample(12, true, 900));
        }

        ConsumptionBaseline baseline = planner.baseline(samples);

        assertEquals(400.0, baseline.wattAt(DayOfWeek.WEDNESDAY, 12), 0.0001);
        assertEquals(900.0, baseline.wattAt(DayOfWeek.SUNDAY, 12), 0.0001);
    }

    @Test
    void baselineNimmtMedianGegenAusreisser() {
        List<ConsumptionSample> samples = new ArrayList<>();
        samples.add(new ConsumptionSample(8, false, 300));
        samples.add(new ConsumptionSample(8, false, 320));
        samples.add(new ConsumptionSample(8, false, 310));
        samples.add(new ConsumptionSample(8, false, 9000)); // Waschmaschine

        ConsumptionBaseline baseline = planner.baseline(samples);

        assertEquals(315.0, baseline.wattAt(DayOfWeek.MONDAY, 8), 0.0001);
    }

    @Test
    void slotOhneDatenErbtDenGesamtmedianNichtNull() {
        // Nur Slot 12 hat Daten. Slot 3 darf NICHT 0 werden, sonst taeuscht die Rechnung
        // spaeter Ueberschuss vor, den es nicht gibt.
        List<ConsumptionSample> samples = List.of(
                new ConsumptionSample(12, false, 500), new ConsumptionSample(12, false, 700));

        ConsumptionBaseline baseline = planner.baseline(samples);

        assertEquals(600.0, baseline.wattAt(DayOfWeek.MONDAY, 3), 0.0001);
    }

    @Test
    void ohneJedeHistorieBleibtDieBaselineBeiNull() {
        ConsumptionBaseline baseline = planner.baseline(List.of());

        assertEquals(0.0, baseline.wattAt(DayOfWeek.MONDAY, 12), 0.0001);
        assertEquals(0.0, planner.baseline(null).wattAt(DayOfWeek.SUNDAY, 0), 0.0001);
    }

    @Test
    void lueckeInDerReiheTrenntZweiFenster() {
        // Zwei Bloecke mit einer fehlenden Stunde dazwischen duerfen nicht zu einem
        // durchgehenden Fenster verschmelzen.
        List<PvForecast.HourEntry> hours = new ArrayList<>();
        Instant start = Instant.parse("2026-08-04T08:00:00Z");
        for (int i : new int[] {0, 1, 4, 5}) {
            hours.add(new PvForecast.HourEntry(start.plusSeconds(i * 3600L), 3000, 0));
        }
        PvForecast forecast = new PvForecast(hours, 0, 0, Confidence.LEARNED, learnedAt, computedAt);

        List<SurplusWindow> windows = planner.windows(forecast, flatBaseline(500), ZURICH, 500, TWO_HOURS);

        assertEquals(2, windows.size());
        // Indizes 0,1,4,5 ab 08:00Z -> zweiter Block beginnt um 12:00Z.
        assertEquals(Instant.parse("2026-08-04T12:00:00Z"), windows.get(1).from());
        assertEquals(Instant.parse("2026-08-04T10:00:00Z"), windows.get(0).to(), "erstes Fenster endet vor der Luecke");
    }

    @Test
    void pflichtparameterWerdenGeprueft() {
        assertThrows(IllegalArgumentException.class,
                () -> planner.windows(null, flatBaseline(0), ZURICH, 500, TWO_HOURS));
        assertThrows(IllegalArgumentException.class,
                () -> planner.windows(forecastWith(1), null, ZURICH, 500, TWO_HOURS));
    }
}
