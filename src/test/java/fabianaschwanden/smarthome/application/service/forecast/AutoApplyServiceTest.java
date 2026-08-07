package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatteryScheduleType;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.forecast.AccuracyHistory;
import fabianaschwanden.smarthome.domain.model.forecast.AutoApplyOutcome;
import fabianaschwanden.smarthome.domain.model.forecast.AutoApplyState;
import fabianaschwanden.smarthome.domain.model.forecast.ForecastAccuracy;
import fabianaschwanden.smarthome.domain.port.in.forecast.ApplyRecommendation;
import fabianaschwanden.smarthome.domain.port.in.forecast.ForecastAccuracyQuery;
import fabianaschwanden.smarthome.domain.port.in.forecast.NoRecommendationAvailable;
import fabianaschwanden.smarthome.domain.port.out.forecast.AutoApplyStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Automatik und ihr Schutzschalter: Wann darf ohne Rueckfrage geschaltet werden? */
class AutoApplyServiceTest {

    private static final LocalDate HEUTE = LocalDate.of(2026, 8, 7);
    private static final Clock UHR = Clock.fixed(Instant.parse("2026-08-07T05:15:00Z"), ZoneOffset.UTC);
    private static final int MIN_TAGE = 5;
    private static final double MAX_MAPE = 30;

    private FakeRepository repository;
    private FakeAccuracy accuracy;
    private FakeApply apply;
    private AutoApplyService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        accuracy = new FakeAccuracy();
        apply = new FakeApply();
        service = new AutoApplyService(repository, accuracy, apply, UHR, 14, MIN_TAGE, MAX_MAPE);
    }

    @Test
    void ist_standardmaessig_aus() {
        // Automatisches Schalten ist eine bewusste Entscheidung, kein Standard.
        assertFalse(service.state().enabled());
    }

    @Test
    void schaltet_nicht_solange_sie_aus_ist() {
        accuracy.history = genaueHistorie(10, 12.0);

        service.run();

        assertEquals(0, apply.calls);
        assertNull(repository.state.lastRunDay());
    }

    @Test
    void uebernimmt_die_empfehlung_bei_bewaehrter_prognose() {
        service.setEnabled(true);
        accuracy.history = genaueHistorie(10, 12.0);

        service.run();

        assertEquals(1, apply.calls);
        assertEquals(AutoApplyOutcome.APPLIED, repository.state.lastOutcome());
    }

    @Test
    void schaltet_nicht_bei_zu_wenigen_ausgewerteten_tagen() {
        // Eine perfekte Trefferquote aus zwei Tagen ist Zufall, keine Trefferquote -
        // sonst schaltete die Automatik direkt nach der Inbetriebnahme los.
        service.setEnabled(true);
        accuracy.history = genaueHistorie(2, 5.0);

        service.run();

        assertEquals(0, apply.calls);
        assertEquals(AutoApplyOutcome.NOT_ENOUGH_DATA, repository.state.lastOutcome());
    }

    @Test
    void schaltet_nicht_bei_unzuverlaessiger_prognose() {
        service.setEnabled(true);
        accuracy.history = genaueHistorie(10, 45.0);

        service.run();

        assertEquals(0, apply.calls);
        assertEquals(AutoApplyOutcome.FORECAST_UNRELIABLE, repository.state.lastOutcome());
        assertTrue(repository.state.lastDetail().contains("30"));
    }

    @Test
    void haelt_den_tag_auch_ohne_schaltung_fest() {
        // Sonst liefe die Automatik nach einem Neustart am selben Tag erneut.
        service.setEnabled(true);
        accuracy.history = genaueHistorie(2, 5.0);

        service.run();

        assertEquals(HEUTE, repository.state.lastRunDay());
    }

    @Test
    void laeuft_pro_tag_nur_einmal() {
        service.setEnabled(true);
        accuracy.history = genaueHistorie(10, 12.0);

        service.run();
        service.run();

        assertEquals(1, apply.calls);
    }

    @Test
    void behandelt_einen_truben_tag_als_fachfall() {
        service.setEnabled(true);
        accuracy.history = genaueHistorie(10, 12.0);
        apply.throwNoRecommendation = true;

        service.run();

        assertEquals(AutoApplyOutcome.NO_RECOMMENDATION, repository.state.lastOutcome());
        assertEquals(HEUTE, repository.state.lastRunDay());
    }

    @Test
    void behaelt_das_ergebnis_des_letzten_laufs_beim_umschalten() {
        service.setEnabled(true);
        accuracy.history = genaueHistorie(10, 12.0);
        service.run();

        AutoApplyState nachAus = service.setEnabled(false);

        assertFalse(nachAus.enabled());
        assertEquals(AutoApplyOutcome.APPLIED, nachAus.lastOutcome());
    }

    /** Historie mit {@code tage} bewerteten Tagen und dem gewuenschten mittleren Fehler. */
    private static AccuracyHistory genaueHistorie(int tage, double fehlerProzent) {
        List<ForecastAccuracy> days = new ArrayList<>();
        for (int i = 0; i < tage; i++) {
            double ist = 10.0;
            double prognose = ist * (1 + fehlerProzent / 100.0);
            days.add(ForecastAccuracy.predicted(HEUTE.minusDays(i), prognose).settledWith(ist));
        }
        return AccuracyHistory.of(days);
    }

    private static final class FakeRepository implements AutoApplyStateRepository {
        private AutoApplyState state = AutoApplyState.disabled();

        @Override
        public AutoApplyState load() {
            return state;
        }

        @Override
        public void save(AutoApplyState newState) {
            state = newState;
        }
    }

    private static final class FakeAccuracy implements ForecastAccuracyQuery {
        private AccuracyHistory history = AccuracyHistory.of(List.of());

        @Override
        public AccuracyHistory accuracy(int days) {
            return history;
        }
    }

    private static final class FakeApply implements ApplyRecommendation {
        private int calls;
        private boolean throwNoRecommendation;

        @Override
        public BatterySchedule apply() {
            calls++;
            if (throwNoRecommendation) {
                throw new NoRecommendationAvailable();
            }
            return new BatterySchedule(UUID.randomUUID(), BatteryScheduleType.COUNTDOWN,
                    RelayState.ON, true, null, null, UHR.instant());
        }
    }
}
