package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
import fabianaschwanden.smarthome.domain.model.forecast.Confidence;
import fabianaschwanden.smarthome.domain.model.forecast.IrradiancePoint;
import fabianaschwanden.smarthome.domain.model.forecast.IrradianceSeries;
import fabianaschwanden.smarthome.domain.model.forecast.PlantProfile;
import fabianaschwanden.smarthome.domain.model.forecast.PvForecast;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySampleRepository;
import fabianaschwanden.smarthome.domain.port.out.forecast.IrradianceGateway;
import fabianaschwanden.smarthome.domain.port.out.forecast.PlantProfileRepository;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verhalten des Prognose-Dienstes gegen handgeschriebene Fake-Ports – kein Container,
 * keine Datenbank, feste Uhr.
 */
@QuarkusTest
class ForecastServiceTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    /** 4. August 2026, 08:00 lokal. */
    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZURICH);

    // --- Fakes ---------------------------------------------------------------

    private static final class FakeIrradiance implements IrradianceGateway {
        Optional<IrradianceSeries> next = Optional.empty();
        int calls;

        @Override
        public Optional<IrradianceSeries> fetch() {
            calls++;
            return next;
        }
    }

    private static final class FakeProfiles implements PlantProfileRepository {
        PlantProfile stored;

        @Override public Optional<PlantProfile> load() { return Optional.ofNullable(stored); }
        @Override public void save(PlantProfile profile) { this.stored = profile; }
    }

    private static final class FakeSamples implements EnergySampleRepository {
        List<EnergySample> samples = new ArrayList<>();

        @Override public void save(EnergySample sample) { samples.add(sample); }
        @Override public List<EnergySample> between(Instant from, Instant to) {
            return samples.stream()
                    .filter(s -> !s.timestamp().isBefore(from) && s.timestamp().isBefore(to))
                    .toList();
        }
        @Override public long deleteOlderThan(Instant cutoff) { return 0; }
        @Override public long total() { return samples.size(); }
    }

    // --- Hilfen --------------------------------------------------------------

    private final FakeIrradiance irradiance = new FakeIrradiance();
    private final FakeProfiles profiles = new FakeProfiles();
    private final FakeSamples samples = new FakeSamples();

    private ForecastService service() {
        return new ForecastService(
                irradiance, profiles, samples, CLOCK, 21, 500, Duration.ofHours(2), 10.0);
    }

    /** Sonnige Stunden ab 09:00 lokal mit konstanter Strahlung. */
    private IrradianceSeries sunnySeries(int hours, double gti) {
        List<IrradiancePoint> forecast = new ArrayList<>();
        Instant start = Instant.parse("2026-08-04T07:00:00Z");
        for (int i = 0; i < hours; i++) {
            forecast.add(new IrradiancePoint(start.plus(Duration.ofHours(i)), gti));
        }
        return new IrradianceSeries(List.of(), forecast, NOW);
    }

    private PlantProfile profile(double factor) {
        return new PlantProfile(
                Collections.nCopies(PlantProfile.SLOTS, factor), 0, NOW, Confidence.LEARNED);
    }

    // --- Tests ---------------------------------------------------------------

    @Test
    void ohneRefreshGibtEsNochKeineProgose() {
        assertTrue(service().currentForecast().isEmpty());
        assertTrue(service().windows().isEmpty());
        assertTrue(service().recommendation().isEmpty());
    }

    @Test
    void refreshRechnetProgoseUndFenster() {
        profiles.stored = profile(5.0);
        irradiance.next = Optional.of(sunnySeries(6, 600));
        ForecastService service = service();

        service.refresh();

        PvForecast forecast = service.currentForecast().orElseThrow();
        assertEquals(6, forecast.hours().size());
        assertEquals(3000.0, forecast.hours().get(0).expectedPvWatt(), 0.0001);
        assertFalse(service.windows().isEmpty(), "3000 W liegen deutlich ueber der Schwelle");
        assertTrue(service.recommendation().isPresent());
    }

    @Test
    void ohneProfilGreiftDerColdStartUndMarkiertGrob() {
        // Kein gespeichertes Profil -> Fallback aus kWp, Prognose als ROUGH markiert.
        irradiance.next = Optional.of(sunnySeries(3, 600));
        ForecastService service = service();

        service.refresh();

        PvForecast forecast = service.currentForecast().orElseThrow();
        assertEquals(Confidence.ROUGH, forecast.confidence());
        // Faktor 8.5 W je W/m² -> 600 W/m² ergeben 5100 W, gedeckelt auf 10000 W.
        assertEquals(8.5 * 600, forecast.hours().get(0).expectedPvWatt(), 0.0001);
    }

    @Test
    void ausfallDerQuelleLaesstDieAlteProgoseStehen() {
        // SPEC §6: die App degradiert, sie faellt nicht aus.
        profiles.stored = profile(5.0);
        irradiance.next = Optional.of(sunnySeries(4, 600));
        ForecastService service = service();
        service.refresh();
        PvForecast erste = service.currentForecast().orElseThrow();

        irradiance.next = Optional.empty();
        service.refresh();

        assertSame(erste, service.currentForecast().orElseThrow(),
                "die letzte Prognose bleibt unveraendert stehen");
    }

    @Test
    void leereVorhersageZaehltWieEinAusfall() {
        profiles.stored = profile(5.0);
        irradiance.next = Optional.of(sunnySeries(4, 600));
        ForecastService service = service();
        service.refresh();

        irradiance.next = Optional.of(new IrradianceSeries(List.of(), List.of(), NOW));
        service.refresh();

        assertEquals(4, service.currentForecast().orElseThrow().hours().size());
    }

    @Test
    void baselineEntstehtAusDerVerbrauchshistorie() {
        profiles.stored = profile(5.0);
        irradiance.next = Optional.of(sunnySeries(4, 600));
        // Zwei Messpunkte in derselben Stunde werden gemittelt (400 und 600 -> 500).
        Instant gestern = NOW.minus(Duration.ofDays(1));
        samples.samples.add(new EnergySample(gestern, 0, 400));
        samples.samples.add(new EnergySample(gestern.plusSeconds(600), 0, 600));
        ForecastService service = service();

        service.refresh();

        assertTrue(service.baseline().isPresent());
        int slot = gestern.atZone(ZURICH).getHour();
        assertEquals(500.0,
                service.baseline().orElseThrow().wattAt(gestern.atZone(ZURICH).getDayOfWeek(), slot),
                0.0001);
    }

    @Test
    void hoherVerbrauchVerhindertDasFenster() {
        profiles.stored = profile(5.0);
        irradiance.next = Optional.of(sunnySeries(6, 600)); // 3000 W erwartet
        // Baseline knapp unter der Erzeugung: Ueberschuss bleibt unter 500 W.
        for (int hour = 0; hour < 24; hour++) {
            Instant t = Instant.parse("2026-08-03T00:00:00Z").plus(Duration.ofHours(hour));
            samples.samples.add(new EnergySample(t, 0, 2700));
        }
        ForecastService service = service();

        service.refresh();

        assertTrue(service.windows().isEmpty(), "300 W Ueberschuss reichen nicht");
        assertTrue(service.recommendation().isEmpty());
    }

    @Test
    void fehlerImGatewayBrichtNichtDenDienst() {
        profiles.stored = profile(5.0);
        IrradianceGateway kaputt = () -> {
            throw new IllegalStateException("Netz weg");
        };
        ForecastService service = new ForecastService(
                kaputt, profiles, samples, CLOCK, 21, 500, Duration.ofHours(2), 10.0);

        service.refresh(); // darf nicht werfen

        assertTrue(service.currentForecast().isEmpty());
    }

    @Test
    void empfehlungTraegtDieConfidenceDerProgose() {
        irradiance.next = Optional.of(sunnySeries(5, 900)); // Cold Start -> ROUGH
        ForecastService service = service();

        service.refresh();

        assertEquals(Confidence.ROUGH, service.recommendation().orElseThrow().confidence());
    }
}
