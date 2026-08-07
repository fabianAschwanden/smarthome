package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.domain.model.coverschedule.CoverSchedule;
import fabianaschwanden.smarthome.domain.model.coverschedule.CoverScheduleType;
import fabianaschwanden.smarthome.domain.model.forecast.Confidence;
import fabianaschwanden.smarthome.domain.model.forecast.PvForecast;
import fabianaschwanden.smarthome.domain.model.sensor.Sensor;
import fabianaschwanden.smarthome.domain.port.in.coverschedule.ManageCoverSchedules;
import fabianaschwanden.smarthome.domain.port.in.forecast.NoHeatProtectionAvailable;
import fabianaschwanden.smarthome.domain.port.in.forecast.PvForecastQuery;
import fabianaschwanden.smarthome.domain.port.in.sensor.ReadSensors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Der Hitzeschutz als Zeitsteuerung - und die Position, auf die zugefahren wird. */
class HeatProtectionServiceTest {

    private static final Instant MITTERNACHT = Instant.parse("2026-08-07T00:00:00Z");
    private static final Clock UHR = Clock.fixed(MITTERNACHT, ZoneOffset.UTC);
    /** Geraeteskala: 2 = "98 % zu" in der Anzeige. */
    private static final int BESCHATTET = 2;

    private FakeForecast forecast;
    private FakeSensors sensors;
    private FakeSchedules schedules;
    private HeatProtectionService service;

    @BeforeEach
    void setUp() {
        forecast = new FakeForecast();
        sensors = new FakeSensors();
        schedules = new FakeSchedules();
        service = new HeatProtectionService(
                forecast, sensors, schedules, UHR,
                "innen", List.of("store-links", "store-mitte"),
                550, 24, Duration.ofHours(2), BESCHATTET, 100);
    }

    @Test
    void faehrt_nicht_ganz_zu_sondern_auf_98_prozent() {
        // Geraeteskala 2 = 98 % zu. Ganz unten (0) sitzt der Behang auf dem Anschlag auf
        // und der Raum wird voellig dunkel - fuer Hitzeschutz braucht es beides nicht.
        heisserTag();

        List<CoverSchedule> angelegt = service.applyHeatProtection();

        List<CoverSchedule> zufahren = angelegt.stream()
                .filter(s -> s.position() != 100)
                .toList();
        assertEquals(2, zufahren.size());
        zufahren.forEach(s -> assertEquals(2, s.position()));
    }

    @Test
    void legt_je_store_ein_zufahren_und_ein_oeffnen_an() {
        heisserTag();

        List<CoverSchedule> angelegt = service.applyHeatProtection();

        assertEquals(4, angelegt.size());
        angelegt.forEach(s -> assertEquals(CoverScheduleType.COUNTDOWN, s.type()));
        assertEquals(
                List.of("store-links", "store-links", "store-mitte", "store-mitte"),
                angelegt.stream().map(CoverSchedule::coverId).toList());
    }

    @Test
    void oeffnet_am_ende_des_fensters_wieder() {
        heisserTag();

        List<CoverSchedule> angelegt = service.applyHeatProtection();

        CoverSchedule oeffnen = angelegt.stream().filter(s -> s.position() == 100).findFirst().orElseThrow();
        assertEquals(MITTERNACHT.plusSeconds(6 * 3600), oeffnen.fireAt());
    }

    @Test
    void empfiehlt_nichts_ohne_innentemperatur() {
        // Ohne Messwert fehlt die halbe Bedingung. Zu raten hiesse, an einem kuehlen
        // Apriltag die Storen zu schliessen, nur weil die Sonne scheint.
        forecast.hours = gti(900, 900, 900, 900, 900, 900);
        sensors.sensors = List.of();

        assertTrue(service.heatProtection().isEmpty());
    }

    @Test
    void uebergeht_einen_offline_gemeldeten_sensor() {
        forecast.hours = gti(900, 900, 900, 900, 900, 900);
        sensors.sensors = List.of(new Sensor("innen", "Innen", "Wohnzimmer", 28.0, 50, false, MITTERNACHT));

        assertTrue(service.heatProtection().isEmpty());
    }

    @Test
    void wirft_beim_uebernehmen_ohne_fenster() {
        forecast.hours = gti(100, 100, 100);
        sensors.sensors = List.of(new Sensor("innen", "Innen", "Wohnzimmer", 28.0, 50, true, MITTERNACHT));

        assertThrows(NoHeatProtectionAvailable.class, () -> service.applyHeatProtection());
        assertTrue(schedules.saved.isEmpty());
    }

    private void heisserTag() {
        forecast.hours = gti(900, 900, 900, 900, 900, 900, 100);
        sensors.sensors = List.of(new Sensor("innen", "Innen", "Wohnzimmer", 28.0, 50, true, MITTERNACHT));
    }

    private static List<PvForecast.HourEntry> gti(double... werte) {
        List<PvForecast.HourEntry> hours = new ArrayList<>();
        for (int i = 0; i < werte.length; i++) {
            hours.add(new PvForecast.HourEntry(MITTERNACHT.plusSeconds(i * 3600L), 0, werte[i]));
        }
        return hours;
    }

    private static final class FakeForecast implements PvForecastQuery {
        private List<PvForecast.HourEntry> hours = List.of();

        @Override
        public Optional<PvForecast> currentForecast() {
            return Optional.of(new PvForecast(hours, 0, 0, Confidence.LEARNED, null, MITTERNACHT));
        }
    }

    private static final class FakeSensors implements ReadSensors {
        private List<Sensor> sensors = List.of();

        @Override
        public List<Sensor> list() {
            return sensors;
        }
    }

    private static final class FakeSchedules implements ManageCoverSchedules {
        private final List<CoverSchedule> saved = new ArrayList<>();

        @Override
        public List<CoverSchedule> all() {
            return saved;
        }

        @Override
        public CoverSchedule save(CoverSchedule schedule) {
            saved.add(schedule);
            return schedule;
        }

        @Override
        public CoverSchedule setEnabled(UUID id, boolean enabled) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(UUID id) {
            throw new UnsupportedOperationException();
        }
    }
}
