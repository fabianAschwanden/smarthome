package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.application.config.WellnessSurplusConfig;
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
        service = new WellnessSurplusService(surplus, schedules, new FakeConfig());
    }

    @Test
    void hebt_zu_fensterbeginn_an_und_senkt_am_ende_zurueck() {
        // Geregelt wird ueber die Soll-Temperatur: Die Heizung eines Gecko-Spas laesst
        // sich nicht schalten, sie folgt dem Sollwert.
        surplus.recommendation = new ChargeRecommendation(FENSTER, Confidence.LEARNED);

        List<ApplianceSchedule> angelegt = service.applyWellnessSurplus();

        assertEquals(4, angelegt.size());
        assertEquals(
                List.of("whirlpool:38:" + VON, "whirlpool:33:" + BIS,
                        "pool:28:" + VON, "pool:24:" + BIS),
                angelegt.stream()
                        .map(s -> s.applianceId() + ":" + s.targetTemp() + ":" + s.fireAt())
                        .toList());
    }

    @Test
    void nimmt_ohne_ladeempfehlung_das_erste_ueberschussfenster() {
        // Die Empfehlung gilt der Batterie und verlangt deren Schwellen; zum Aufheizen
        // reicht auch ein kleineres Fenster.
        surplus.windows = List.of(FENSTER);

        List<ApplianceSchedule> angelegt = service.applyWellnessSurplus();

        assertEquals(4, angelegt.size());
        assertEquals(38, angelegt.get(0).targetTemp());
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

    /** Whirlpool 33 -> 38 °C, Becken 24 -> 28 °C. */
    private static final class FakeConfig implements WellnessSurplusConfig {

        @Override
        public List<Entry> appliances() {
            return List.of(entry("whirlpool", 33, 38), entry("pool", 24, 28));
        }

        private static Entry entry(String id, int base, int surplus) {
            return new Entry() {
                @Override
                public String id() {
                    return id;
                }

                @Override
                public int baseTemp() {
                    return base;
                }

                @Override
                public int surplusTemp() {
                    return surplus;
                }
            };
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
