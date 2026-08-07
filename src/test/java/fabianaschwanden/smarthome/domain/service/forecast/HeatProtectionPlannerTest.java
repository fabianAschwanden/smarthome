package fabianaschwanden.smarthome.domain.service.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.Confidence;
import fabianaschwanden.smarthome.domain.model.forecast.HeatProtectionWindow;
import fabianaschwanden.smarthome.domain.model.forecast.PvForecast;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wann ein Beschattungsfenster entsteht - und wann bewusst keines. */
class HeatProtectionPlannerTest {

    private static final Instant MITTERNACHT = Instant.parse("2026-08-07T00:00:00Z");
    private static final double GTI_SCHWELLE = 550;
    private static final double INNEN_SCHWELLE = 24;
    private static final Duration MINDESTDAUER = Duration.ofHours(2);

    private final HeatProtectionPlanner planner = new HeatProtectionPlanner();

    /** Prognose mit den GTI-Werten ab 00:00 UTC, ein Wert je Stunde. */
    private static PvForecast forecastMitGti(double... gtiProStunde) {
        List<PvForecast.HourEntry> hours = new ArrayList<>();
        for (int i = 0; i < gtiProStunde.length; i++) {
            hours.add(new PvForecast.HourEntry(MITTERNACHT.plusSeconds(i * 3600L), 0, gtiProStunde[i]));
        }
        return new PvForecast(hours, 0, 0, Confidence.LEARNED, null, MITTERNACHT);
    }

    private Optional<HeatProtectionWindow> plan(PvForecast forecast, double innen, Instant jetzt) {
        return planner.plan(forecast, innen, jetzt, GTI_SCHWELLE, INNEN_SCHWELLE, MINDESTDAUER);
    }

    @Test
    void findet_das_fenster_starker_einstrahlung() {
        PvForecast forecast = forecastMitGti(0, 100, 300, 600, 800, 700, 200);

        HeatProtectionWindow fenster = plan(forecast, 26.0, MITTERNACHT).orElseThrow();

        assertEquals(MITTERNACHT.plusSeconds(3 * 3600), fenster.from());
        assertEquals(MITTERNACHT.plusSeconds(6 * 3600), fenster.to());
        assertEquals(800, fenster.peakGti());
        assertEquals(26.0, fenster.indoorTemp());
    }

    @Test
    void beschattet_nicht_bei_kuehlen_raeumen() {
        // Ein strahlender Wintertag heizt das Haus nicht auf. Ohne diese Bedingung
        // naehme man dem Wohnzimmer grundlos das Licht.
        PvForecast forecast = forecastMitGti(0, 900, 900, 900);

        assertTrue(plan(forecast, 19.0, MITTERNACHT).isEmpty());
    }

    @Test
    void beschattet_nicht_bei_schwacher_einstrahlung() {
        PvForecast forecast = forecastMitGti(100, 200, 300, 200);

        assertTrue(plan(forecast, 28.0, MITTERNACHT).isEmpty());
    }

    @Test
    void verwirft_ein_zu_kurzes_fenster() {
        // Fuer eine Stunde Sonne die Storen zu fahren kostet mehr Aufmerksamkeit,
        // als es Waerme spart.
        PvForecast forecast = forecastMitGti(0, 900, 100, 100);

        assertTrue(plan(forecast, 28.0, MITTERNACHT).isEmpty());
    }

    @Test
    void beendet_das_fenster_an_der_ersten_luecke() {
        // Ein zweites Hoch am Nachmittag ist ein eigenes Fenster - nicht eine
        // Verlaengerung ueber die Wolken hinweg.
        PvForecast forecast = forecastMitGti(600, 700, 600, 100, 900, 900, 900);

        HeatProtectionWindow fenster = plan(forecast, 28.0, MITTERNACHT).orElseThrow();

        assertEquals(MITTERNACHT.plusSeconds(3 * 3600), fenster.to());
    }

    @Test
    void beginnt_ein_laufendes_fenster_jetzt() {
        // Rueckwirkend beschatten geht nicht.
        PvForecast forecast = forecastMitGti(900, 900, 900, 900, 900, 900, 100);
        Instant jetzt = MITTERNACHT.plusSeconds(2 * 3600 + 1800);

        HeatProtectionWindow fenster = plan(forecast, 28.0, jetzt).orElseThrow();

        assertEquals(jetzt, fenster.from());
        assertEquals(MITTERNACHT.plusSeconds(6 * 3600), fenster.to());
    }

    @Test
    void verwirft_ein_fenster_das_ohnehin_gleich_vorbei_ist() {
        // Die Mindestdauer gilt fuer die verbleibende Zeit, nicht fuer das ganze
        // Fenster: Beschattet wird fuer die kommenden Stunden, nicht fuer die
        // vergangenen. Bleiben nur noch 90 Minuten, lohnt sich das Fahren nicht mehr.
        PvForecast forecast = forecastMitGti(900, 900, 900, 900, 100);
        Instant jetzt = MITTERNACHT.plusSeconds(2 * 3600 + 1800);

        assertTrue(plan(forecast, 28.0, jetzt).isEmpty());
    }

    @Test
    void uebergeht_vergangene_stunden() {
        PvForecast forecast = forecastMitGti(900, 900, 100, 100, 800, 800, 800, 100);
        Instant jetzt = MITTERNACHT.plusSeconds(3 * 3600);

        HeatProtectionWindow fenster = plan(forecast, 28.0, jetzt).orElseThrow();

        assertEquals(MITTERNACHT.plusSeconds(4 * 3600), fenster.from());
    }

    @Test
    void kommt_ohne_prognose_zurecht() {
        assertTrue(plan(null, 28.0, MITTERNACHT).isEmpty());
    }
}
