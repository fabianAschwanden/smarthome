package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.domain.model.energy.EnergyBucket;
import fabianaschwanden.smarthome.domain.model.energy.EnergyHistory;
import fabianaschwanden.smarthome.domain.model.energy.HistoryRange;
import fabianaschwanden.smarthome.domain.model.forecast.Confidence;
import fabianaschwanden.smarthome.domain.model.forecast.ForecastAccuracy;
import fabianaschwanden.smarthome.domain.model.forecast.PvForecast;
import fabianaschwanden.smarthome.domain.port.in.energy.EnergyHistoryQuery;
import fabianaschwanden.smarthome.domain.port.in.forecast.PvForecastQuery;
import fabianaschwanden.smarthome.domain.port.out.forecast.ForecastAccuracyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wann die Prognose festgeschrieben wird und wann der Ist-Wert nachgetragen werden darf. */
class ForecastAccuracyServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Zurich");
    private static final LocalDate HEUTE = LocalDate.of(2026, 8, 7);
    private static final LocalDate GESTERN = HEUTE.minusDays(1);
    // 07:00 Ortszeit am 7. August.
    private static final Clock UHR = Clock.fixed(Instant.parse("2026-08-07T05:00:00Z"), ZONE);

    private FakeRepository repository;
    private FakeForecast forecast;
    private FakeHistory history;
    private ForecastAccuracyService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        forecast = new FakeForecast();
        history = new FakeHistory();
        service = new ForecastAccuracyService(repository, forecast, history, UHR);
    }

    @Test
    void haelt_die_prognose_des_tages_fest() {
        forecast.todayKwh = 24.5;

        service.recordToday();

        assertEquals(24.5, repository.byDate(HEUTE).orElseThrow().forecastKwh());
        assertFalse(repository.byDate(HEUTE).orElseThrow().isSettled());
    }

    @Test
    void bessert_eine_festgehaltene_prognose_nicht_nach() {
        // Sonst schmiegte sich die Zahl im Lauf des Tages an die Wirklichkeit an und
        // liesse die Auswertung besser aussehen, als die Prognose war.
        forecast.todayKwh = 24.5;
        service.recordToday();

        forecast.todayKwh = 12.0;
        service.recordToday();

        assertEquals(24.5, repository.byDate(HEUTE).orElseThrow().forecastKwh());
    }

    @Test
    void haelt_ohne_prognose_nichts_fest() {
        forecast.todayKwh = null;

        service.recordToday();

        assertTrue(repository.byDate(HEUTE).isEmpty());
    }

    @Test
    void traegt_den_ist_wert_des_vortags_nach() {
        repository.save(ForecastAccuracy.predicted(GESTERN, 20.0));
        history.pvKwhByDay.put(GESTERN, 18.0);

        service.settle(GESTERN);

        ForecastAccuracy eintrag = repository.byDate(GESTERN).orElseThrow();
        assertEquals(18.0, eintrag.actualKwh().getAsDouble());
        // Der Fehler bezieht sich auf den Ist-Wert (MAPE-Konvention): 2 von 18 kWh.
        assertEquals(11.111, eintrag.deviationPercent().getAsDouble(), 0.001);
    }

    @Test
    void laesst_den_ist_wert_ohne_messwerte_offen() {
        // 0 einzutragen hiesse zu behaupten, die Anlage habe nichts produziert - dabei
        // fehlen nur die Daten.
        repository.save(ForecastAccuracy.predicted(GESTERN, 20.0));

        service.settle(GESTERN);

        assertFalse(repository.byDate(GESTERN).orElseThrow().isSettled());
    }

    @Test
    void traegt_einen_bereits_abgeschlossenen_tag_nicht_erneut_ein() {
        repository.save(ForecastAccuracy.predicted(GESTERN, 20.0).settledWith(18.0));
        history.pvKwhByDay.put(GESTERN, 5.0);

        service.settle(GESTERN);

        assertEquals(18.0, repository.byDate(GESTERN).orElseThrow().actualKwh().getAsDouble());
    }

    @Test
    void uebergeht_einen_tag_ohne_festgehaltene_prognose() {
        history.pvKwhByDay.put(GESTERN, 18.0);

        service.settle(GESTERN);

        assertTrue(repository.byDate(GESTERN).isEmpty());
    }

    @Test
    void liefert_die_historie_mit_mape() {
        repository.save(ForecastAccuracy.predicted(GESTERN, 12.0).settledWith(10.0));
        repository.save(ForecastAccuracy.predicted(HEUTE, 9.0));

        assertEquals(20.0, service.accuracy(14).mapePercent().getAsDouble(), 0.001);
        assertEquals(2, service.accuracy(14).days().size());
    }

    private static final class FakeRepository implements ForecastAccuracyRepository {
        private final Map<LocalDate, ForecastAccuracy> entries = new HashMap<>();

        @Override
        public void save(ForecastAccuracy accuracy) {
            entries.put(accuracy.date(), accuracy);
        }

        @Override
        public Optional<ForecastAccuracy> byDate(LocalDate date) {
            return Optional.ofNullable(entries.get(date));
        }

        @Override
        public List<ForecastAccuracy> latest(int limit) {
            List<ForecastAccuracy> all = new ArrayList<>(entries.values());
            all.sort(Comparator.comparing(ForecastAccuracy::date).reversed());
            return all.size() <= limit ? all : all.subList(0, limit);
        }
    }

    private static final class FakeForecast implements PvForecastQuery {
        private Double todayKwh;

        @Override
        public Optional<PvForecast> currentForecast() {
            return todayKwh == null
                    ? Optional.empty()
                    : Optional.of(new PvForecast(
                            List.of(), todayKwh, 0.0, Confidence.ROUGH, null, UHR.instant()));
        }
    }

    private static final class FakeHistory implements EnergyHistoryQuery {
        private final Map<LocalDate, Double> pvKwhByDay = new HashMap<>();

        @Override
        public EnergyHistory history(HistoryRange range) {
            List<EnergyBucket> buckets = pvKwhByDay.entrySet().stream()
                    .map(e -> new EnergyBucket(
                            e.getKey().atStartOfDay(ZONE).toInstant(), e.getValue(), 0, 0))
                    .toList();
            return new EnergyHistory(range, buckets, List.of());
        }
    }
}
