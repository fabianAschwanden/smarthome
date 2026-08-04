package fabianaschwanden.smarthome.domain.service.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.ChargeRecommendation;
import fabianaschwanden.smarthome.domain.model.forecast.ConsumptionBaseline;
import fabianaschwanden.smarthome.domain.model.forecast.ConsumptionSample;
import fabianaschwanden.smarthome.domain.model.forecast.PvForecast;
import fabianaschwanden.smarthome.domain.model.forecast.SurplusWindow;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Leitet aus Prognose und typischem Verbrauch die Zeitfenster ab, in denen mehr Sonne
 * erwartet wird als das Haus braucht – und daraus die Ladeempfehlung. Reiner
 * Domain-Service: zustandslos, framework-frei, Zeitzone als Parameter.
 */
public final class SurplusPlanner {

    private static final double WH_TO_KWH = 1000.0;

    /**
     * Baseline aus der Historie: je Stunden-Slot der Median des Hausverbrauchs, getrennt
     * nach Werktag und Wochenende.
     *
     * <p>Ein Slot ohne eigene Daten bekommt den Median über alle vorhandenen Slots
     * derselben Gruppe – bewusst nicht 0: eine Null-Baseline würde Überschuss vortäuschen
     * und zu Ladefenstern führen, die es gar nicht gibt. Fehlt die Gruppe komplett, bleibt
     * 0 als einzig ehrlicher Wert.
     */
    public ConsumptionBaseline baseline(List<ConsumptionSample> samples) {
        List<List<Double>> weekday = emptySlots();
        List<List<Double>> weekend = emptySlots();
        if (samples != null) {
            for (ConsumptionSample sample : samples) {
                List<List<Double>> target = sample.weekend() ? weekend : weekday;
                target.get(sample.hourOfDay()).add(sample.consumptionWatt());
            }
        }
        return new ConsumptionBaseline(medianPerSlot(weekday), medianPerSlot(weekend));
    }

    /**
     * Zusammenhängende Stunden, deren erwarteter Überschuss über {@code minWatt} liegt und
     * die mindestens {@code minDuration} dauern. Die Prognose muss dafür chronologisch
     * sortiert sein – so liefert sie der {@link PvForecaster}.
     */
    public List<SurplusWindow> windows(
            PvForecast forecast,
            ConsumptionBaseline baseline,
            ZoneId zone,
            double minWatt,
            Duration minDuration) {
        if (forecast == null || baseline == null || zone == null || minDuration == null) {
            throw new IllegalArgumentException("forecast, baseline, zone und minDuration sind Pflicht");
        }
        List<SurplusWindow> windows = new ArrayList<>();
        List<PvForecast.HourEntry> run = new ArrayList<>();
        List<Double> runSurplus = new ArrayList<>();

        for (PvForecast.HourEntry entry : forecast.hours()) {
            ZonedDateTime local = entry.hour().atZone(zone);
            double surplus = entry.expectedPvWatt()
                    - baseline.wattAt(local.getDayOfWeek(), local.getHour());
            boolean contiguous = run.isEmpty()
                    || entry.hour().equals(run.get(run.size() - 1).hour().plus(Duration.ofHours(1)));
            if (surplus >= minWatt && contiguous) {
                run.add(entry);
                runSurplus.add(surplus);
            } else {
                closeRun(windows, run, runSurplus, minDuration);
                if (surplus >= minWatt) {
                    run.add(entry);
                    runSurplus.add(surplus);
                }
            }
        }
        closeRun(windows, run, runSurplus, minDuration);
        return List.copyOf(windows);
    }

    /** Das Fenster mit der grössten Energiesumme – die eigentliche Ladeempfehlung. */
    public Optional<SurplusWindow> best(List<SurplusWindow> windows) {
        if (windows == null || windows.isEmpty()) {
            return Optional.empty();
        }
        return windows.stream().max(Comparator.comparingDouble(SurplusWindow::expectedKwh));
    }

    /** Bequemer Gesamtweg: Fenster bilden, bestes wählen, mit der Confidence versehen. */
    public Optional<ChargeRecommendation> recommend(
            PvForecast forecast,
            ConsumptionBaseline baseline,
            ZoneId zone,
            double minWatt,
            Duration minDuration) {
        return best(windows(forecast, baseline, zone, minWatt, minDuration))
                .map(window -> new ChargeRecommendation(window, forecast.confidence()));
    }

    /**
     * Schliesst eine laufende Serie ab. Das Fenster endet eine Stunde nach dem letzten
     * Eintrag, weil ein Stundeneintrag die ganze Stunde abdeckt ({@code to} ist exklusiv).
     */
    private void closeRun(
            List<SurplusWindow> windows,
            List<PvForecast.HourEntry> run,
            List<Double> runSurplus,
            Duration minDuration) {
        if (run.isEmpty()) {
            return;
        }
        var from = run.get(0).hour();
        var to = run.get(run.size() - 1).hour().plus(Duration.ofHours(1));
        if (Duration.between(from, to).compareTo(minDuration) >= 0) {
            double sum = runSurplus.stream().mapToDouble(Double::doubleValue).sum();
            double peak = runSurplus.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            windows.add(new SurplusWindow(from, to, sum / WH_TO_KWH, peak));
        }
        run.clear();
        runSurplus.clear();
    }

    private List<List<Double>> emptySlots() {
        List<List<Double>> slots = new ArrayList<>(ConsumptionBaseline.SLOTS);
        for (int i = 0; i < ConsumptionBaseline.SLOTS; i++) {
            slots.add(new ArrayList<>());
        }
        return slots;
    }

    private List<Double> medianPerSlot(List<List<Double>> slots) {
        List<Double> all = slots.stream().flatMap(List::stream).toList();
        double fallback = all.isEmpty() ? 0.0 : PlantProfileLearner.median(all);
        List<Double> result = new ArrayList<>(ConsumptionBaseline.SLOTS);
        for (List<Double> slot : slots) {
            result.add(slot.isEmpty() ? fallback : PlantProfileLearner.median(slot));
        }
        return result;
    }
}
