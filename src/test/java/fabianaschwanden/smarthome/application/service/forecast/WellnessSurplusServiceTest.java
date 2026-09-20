package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.application.config.WellnessConfig;
import fabianaschwanden.smarthome.domain.model.applianceschedule.ApplianceSchedule;
import fabianaschwanden.smarthome.domain.model.forecast.ChargeRecommendation;
import fabianaschwanden.smarthome.domain.model.forecast.Confidence;
import fabianaschwanden.smarthome.domain.model.forecast.ConsumptionBaseline;
import fabianaschwanden.smarthome.domain.model.forecast.SurplusWindow;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatteryScheduleType;
import fabianaschwanden.smarthome.domain.port.in.applianceschedule.ManageApplianceSchedules;
import fabianaschwanden.smarthome.domain.port.in.batteryschedule.ManageBatterySchedules;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Wellness-Heizung im Ueberschussfenster. */
class WellnessSurplusServiceTest {

    private static final Instant VON = Instant.parse("2026-08-07T10:00:00Z");
    private static final Instant BIS = Instant.parse("2026-08-07T14:00:00Z");
    private static final SurplusWindow FENSTER = new SurplusWindow(VON, BIS, 6.4, 2200);

    private FakeSurplus surplus;
    private FakeSchedules schedules;
    private FakeBatterySchedules batterySchedules;
    private FakeAppliances appliances;
    private WellnessSurplusService service;

    @BeforeEach
    void setUp() {
        surplus = new FakeSurplus();
        schedules = new FakeSchedules();
        batterySchedules = new FakeBatterySchedules();
        // Uhr in der Zone der Anlage: Die Kappung am Abend rechnet in Ortszeit.
        appliances = new FakeAppliances();
        service = new WellnessSurplusService(surplus, schedules, batterySchedules, appliances, new FakeConfig(),
                java.time.Clock.fixed(VON, java.time.ZoneId.of("Europe/Zurich")));
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
    void plant_eine_stillgelegte_anlage_nicht_ein() {
        // Das Becken ist ueber den Winter vom Strom. Ein Heizauftrag dafuer wuerde beim
        // Faelligwerden ohnehin verworfen und stuende bis dahin nur in der Liste.
        surplus.recommendation = new ChargeRecommendation(FENSTER, Confidence.LEARNED);
        appliances.deactivated.add("pool");

        List<ApplianceSchedule> angelegt = service.applyWellnessSurplus();

        assertEquals(2, angelegt.size());
        assertTrue(angelegt.stream().allMatch(s -> s.applianceId().equals("whirlpool")));
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
    void schaltet_einen_ladeauftrag_im_heizfenster_ab() {
        // Ein Batterie-Countdown setzt den Manuell-Modus und laedt unabhaengig vom
        // tatsaechlichen Ueberschuss - zusammen mit der Heizung kaeme der Rest aus dem Netz.
        surplus.recommendation = new ChargeRecommendation(FENSTER, Confidence.LEARNED);
        BatterySchedule imFenster = countdown(VON.plusSeconds(600));

        service.applyWellnessSurplus();

        assertFalse(batterySchedules.byId(imFenster.id()).enabled());
    }

    @Test
    void laesst_einen_ladeauftrag_ausserhalb_des_fensters_in_ruhe() {
        surplus.recommendation = new ChargeRecommendation(FENSTER, Confidence.LEARNED);
        BatterySchedule danach = countdown(BIS.plusSeconds(3600));

        service.applyWellnessSurplus();

        assertTrue(batterySchedules.byId(danach.id()).enabled());
    }

    @Test
    void kappt_das_fenster_an_der_abendabsenkung() {
        // Ohne Kappung hoebe der Auftrag am Fensterende die Temperatur nach der
        // Absenkung wieder an - und der Whirlpool heizte doch in den Abend hinein.
        WellnessSurplusService frueheAbsenkung = new WellnessSurplusService(
                surplus, schedules, batterySchedules, appliances, new FakeConfig() {
                    @Override
                    public java.time.LocalTime setbackTime() {
                        return java.time.LocalTime.of(15, 0);  // Ortszeit = 13:00 UTC, mitten im Fenster
                    }
                },
                java.time.Clock.fixed(VON, java.time.ZoneId.of("Europe/Zurich")));
        surplus.recommendation = new ChargeRecommendation(FENSTER, Confidence.LEARNED);  // 10:00-14:00 UTC

        List<ApplianceSchedule> angelegt = frueheAbsenkung.applyWellnessSurplus();

        ApplianceSchedule ende = angelegt.get(1);
        assertEquals(java.time.Instant.parse("2026-08-07T13:00:00Z"), ende.fireAt());
        assertEquals(20, ende.targetTemp());  // Nachttemperatur, nicht die Tagestemperatur
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

    private BatterySchedule countdown(java.time.Instant fireAt) {
        BatterySchedule schedule = new BatterySchedule(
                UUID.randomUUID(), BatteryScheduleType.COUNTDOWN, RelayState.ON, true, null, null, fireAt);
        batterySchedules.save(schedule);
        return schedule;
    }

    private static final class FakeBatterySchedules implements ManageBatterySchedules {
        private final java.util.Map<UUID, BatterySchedule> entries = new java.util.LinkedHashMap<>();

        BatterySchedule byId(UUID id) {
            return entries.get(id);
        }

        @Override
        public List<BatterySchedule> all() {
            return List.copyOf(entries.values());
        }

        @Override
        public BatterySchedule save(BatterySchedule schedule) {
            entries.put(schedule.id(), schedule);
            return schedule;
        }

        @Override
        public BatterySchedule setEnabled(UUID id, boolean enabled) {
            BatterySchedule updated = entries.get(id).withEnabled(enabled);
            entries.put(id, updated);
            return updated;
        }

        @Override
        public void delete(UUID id) {
            entries.remove(id);
        }
    }

    /** Whirlpool 33 -> 38 -> nachts 20 °C, Becken 24 -> 28 -> nachts 18 °C. */
    private static class FakeConfig implements WellnessConfig {

        @Override
        public java.time.LocalTime setbackTime() {
            // Spaet genug, damit das Testfenster (bis 16:00 UTC = 18:00 Ortszeit) nicht
            // gekappt wird; die Kappung hat ihren eigenen Test.
            return java.time.LocalTime.of(23, 0);
        }

        @Override
        public String setbackCheckInterval() {
            return "1m";
        }

        @Override
        public List<Entry> appliances() {
            return List.of(entry("whirlpool", 33, 38, 20), entry("pool", 24, 28, 18));
        }

        private static Entry entry(String id, int base, int surplus, int night) {
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

                @Override
                public int nightTemp() {
                    return night;
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
    /** Weiss nur, welche Anlagen stillgelegt sind - mehr braucht der Ueberschussplan nicht. */
    private static final class FakeAppliances
            implements fabianaschwanden.smarthome.domain.port.in.appliance.ControlAppliances {
        private final java.util.Set<String> deactivated = new java.util.HashSet<>();

        @Override public List<fabianaschwanden.smarthome.domain.model.appliance.Appliance> list() {
            return List.of();
        }

        @Override public fabianaschwanden.smarthome.domain.model.appliance.Appliance switchFunction(
                String id, fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction f,
                fabianaschwanden.smarthome.domain.model.appliance.FunctionState s) {
            throw new UnsupportedOperationException();
        }

        @Override public fabianaschwanden.smarthome.domain.model.appliance.Appliance setTargetTemperature(
                String id, int target) {
            throw new UnsupportedOperationException();
        }

        @Override public java.util.OptionalInt pendingTarget(String id) {
            return java.util.OptionalInt.empty();
        }

        @Override public fabianaschwanden.smarthome.domain.model.appliance.Appliance setActive(
                String id, boolean active) {
            throw new UnsupportedOperationException();
        }

        @Override public boolean isActive(String id) {
            return !deactivated.contains(id);
        }
    }
}
