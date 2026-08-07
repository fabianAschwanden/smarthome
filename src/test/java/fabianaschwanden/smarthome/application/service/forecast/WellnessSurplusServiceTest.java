package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.model.applianceschedule.ApplianceSchedule;
import fabianaschwanden.smarthome.domain.model.forecast.ChargeRecommendation;
import fabianaschwanden.smarthome.domain.model.forecast.Confidence;
import fabianaschwanden.smarthome.domain.model.forecast.ConsumptionBaseline;
import fabianaschwanden.smarthome.domain.model.forecast.SurplusWindow;
import fabianaschwanden.smarthome.domain.port.in.applianceschedule.ManageApplianceSchedules;
import fabianaschwanden.smarthome.domain.port.in.forecast.NoRecommendationAvailable;
import fabianaschwanden.smarthome.domain.port.in.forecast.SurplusQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Wellness-Heizung im Ueberschussfenster. */
class WellnessSurplusServiceTest {

    private static final Instant VON = Instant.parse("2026-08-07T10:00:00Z");
    private static final Instant BIS = Instant.parse("2026-08-07T14:00:00Z");
    private static final SurplusWindow FENSTER = new SurplusWindow(VON, BIS, 6.4, 2200);

    private FakeSurplus surplus;
    private FakeSchedules schedules;
    private WellnessSurplusService service;

    @BeforeEach
    void setUp() {
        surplus = new FakeSurplus();
        schedules = new FakeSchedules();
        service = new WellnessSurplusService(surplus, schedules, List.of("whirlpool", "pool"));
    }

    @Test
    void legt_je_anlage_ein_ein_und_ein_ausschalten_an() {
        surplus.recommendation = new ChargeRecommendation(FENSTER, Confidence.LEARNED);

        List<ApplianceSchedule> angelegt = service.applyWellnessSurplus();

        assertEquals(4, angelegt.size());
        assertTrue(angelegt.stream().allMatch(s -> s.function() == ApplianceFunction.HEATER));
        assertEquals(
                List.of("whirlpool:ON:" + VON, "whirlpool:OFF:" + BIS,
                        "pool:ON:" + VON, "pool:OFF:" + BIS),
                angelegt.stream().map(s -> s.applianceId() + ":" + s.state() + ":" + s.fireAt()).toList());
    }

    @Test
    void nimmt_ohne_ladeempfehlung_das_erste_ueberschussfenster() {
        // Die Empfehlung gilt der Batterie und verlangt deren Schwellen; zum Aufheizen
        // reicht auch ein kleineres Fenster.
        surplus.windows = List.of(FENSTER);

        List<ApplianceSchedule> angelegt = service.applyWellnessSurplus();

        assertEquals(4, angelegt.size());
        assertEquals(FunctionState.ON, angelegt.get(0).state());
    }

    @Test
    void wirft_ohne_jedes_fenster() {
        assertThrows(NoRecommendationAvailable.class, () -> service.applyWellnessSurplus());
        assertTrue(schedules.saved.isEmpty());
    }

    private static final class FakeSurplus implements SurplusQuery {
        private ChargeRecommendation recommendation;
        private List<SurplusWindow> windows = List.of();

        @Override
        public Optional<ConsumptionBaseline> baseline() {
            return Optional.empty();
        }

        @Override
        public List<SurplusWindow> windows() {
            return windows;
        }

        @Override
        public Optional<ChargeRecommendation> recommendation() {
            return Optional.ofNullable(recommendation);
        }
    }

    private static final class FakeSchedules implements ManageApplianceSchedules {
        private final List<ApplianceSchedule> saved = new ArrayList<>();

        @Override
        public List<ApplianceSchedule> all() {
            return saved;
        }

        @Override
        public ApplianceSchedule save(ApplianceSchedule schedule) {
            saved.add(schedule);
            return schedule;
        }

        @Override
        public void delete(UUID id) {
            throw new UnsupportedOperationException();
        }
    }
}
