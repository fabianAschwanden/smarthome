package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
import fabianaschwanden.smarthome.domain.model.forecast.Confidence;
import fabianaschwanden.smarthome.domain.model.forecast.IrradiancePoint;
import fabianaschwanden.smarthome.domain.model.forecast.IrradianceSeries;
import fabianaschwanden.smarthome.domain.model.forecast.PlantProfile;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Der nächtliche Lernlauf gegen handgeschriebene Fake-Ports. */
@QuarkusTest
class ProfileLearningServiceTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final Instant NOW = Instant.parse("2026-08-04T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZURICH);

    private static final class FakeIrradiance implements IrradianceGateway {
        Optional<IrradianceSeries> next = Optional.empty();

        @Override public Optional<IrradianceSeries> fetch() { return next; }
    }

    private static final class FakeProfiles implements PlantProfileRepository {
        PlantProfile stored;
        int saves;

        @Override public Optional<PlantProfile> load() { return Optional.ofNullable(stored); }
        @Override public void save(PlantProfile profile) { stored = profile; saves++; }
    }

    private static final class FakeSamples implements EnergySampleRepository {
        final List<EnergySample> samples = new ArrayList<>();

        @Override public void save(EnergySample sample) { samples.add(sample); }
        @Override public List<EnergySample> between(Instant from, Instant to) {
            return samples.stream()
                    .filter(s -> !s.timestamp().isBefore(from) && s.timestamp().isBefore(to))
                    .toList();
        }
        @Override public long deleteOlderThan(Instant cutoff) { return 0; }
        @Override public long total() { return samples.size(); }
    }

    private final FakeIrradiance irradiance = new FakeIrradiance();
    private final FakeProfiles profiles = new FakeProfiles();
    private final FakeSamples samples = new FakeSamples();

    private ForecastService forecastService() {
        return new ForecastService(
                irradiance, profiles, samples, CLOCK, 21, 500, Duration.ofHours(2), 10.0);
    }

    private ProfileLearningService service() {
        return new ProfileLearningService(irradiance, samples, profiles, forecastService(), CLOCK, 21);
    }

    /**
     * Baut n Tage mit je einer sonnigen Stunde: Strahlung {@code gti} und Leistung
     * {@code gti × factor} – der Lernlauf muss genau {@code factor} herausbekommen.
     */
    private void historieMitFaktor(double factor, double gti, int tage, int stundeUtc) {
        List<IrradiancePoint> past = new ArrayList<>();
        for (int tag = 1; tag <= tage; tag++) {
            Instant hour = NOW.minus(Duration.ofDays(tag)).truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                    .plus(Duration.ofHours(stundeUtc));
            past.add(new IrradiancePoint(hour, gti));
            // Zwei Messpunkte in der Stunde -> werden gemittelt.
            samples.samples.add(new EnergySample(hour, gti * factor, 300));
            samples.samples.add(new EnergySample(hour.plusSeconds(600), gti * factor, 300));
        }
        irradiance.next = Optional.of(new IrradianceSeries(past, List.of(), NOW));
    }

    @Test
    void lerntFaktorAusUeberlappendenStunden() {
        historieMitFaktor(6.0, 500, 6, 10);

        service().learn();

        PlantProfile profile = profiles.stored;
        assertEquals(1, profiles.saves);
        assertEquals(Confidence.LEARNED, profile.confidence());
        int slot = NOW.minus(Duration.ofDays(1)).truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                .plus(Duration.ofHours(10)).atZone(ZURICH).getHour();
        assertEquals(6.0, profile.factorAt(slot), 0.0001);
    }

    @Test
    void ohneStrahlungWirdNichtGelernt() {
        irradiance.next = Optional.empty();

        service().learn();

        assertNull(profiles.stored);
        assertEquals(0, profiles.saves);
    }

    @Test
    void ohneUeberlappendeMesspunkteBleibtDasAlteProfil() {
        // Strahlung vorhanden, aber keine Leistungsmesspunkte in denselben Stunden.
        PlantProfile alt = new PlantProfile(
                Collections.nCopies(PlantProfile.SLOTS, 3.0), 5000, NOW, Confidence.LEARNED);
        profiles.stored = alt;
        irradiance.next = Optional.of(new IrradianceSeries(
                List.of(new IrradiancePoint(NOW.minus(Duration.ofDays(1)), 600)), List.of(), NOW));

        service().learn();

        assertSame(alt, profiles.stored, "ein halb gelerntes Profil waere schlechter als das alte");
        assertEquals(0, profiles.saves);
    }

    @Test
    void zuWenigDatenpunkteAendernNichts() {
        // Nur 2 Tage -> unter MIN_SAMPLES_PER_SLOT, kein Slot qualifiziert sich.
        historieMitFaktor(6.0, 500, 2, 10);

        service().learn();

        assertNull(profiles.stored);
    }

    @Test
    void fehlerImGatewayLaesstDasProfilUnangetastet() {
        PlantProfile alt = new PlantProfile(
                Collections.nCopies(PlantProfile.SLOTS, 3.0), 5000, NOW, Confidence.LEARNED);
        profiles.stored = alt;
        IrradianceGateway kaputt = () -> {
            throw new IllegalStateException("Netz weg");
        };
        ProfileLearningService service = new ProfileLearningService(
                kaputt, samples, profiles, forecastService(), CLOCK, 21);

        service.learn(); // darf nicht werfen

        assertSame(alt, profiles.stored);
    }

    @Test
    void nachDemLernenIstDieProgoseNeuGerechnet() {
        // Der Lernlauf muss die Prognose anstossen, sonst zeigt das UI bis zum naechsten
        // Refresh Zahlen aus dem alten Profil.
        historieMitFaktor(6.0, 500, 6, 10);
        List<IrradiancePoint> zukunft = List.of(
                new IrradiancePoint(NOW.plus(Duration.ofHours(8)), 600),
                new IrradiancePoint(NOW.plus(Duration.ofHours(9)), 600),
                new IrradiancePoint(NOW.plus(Duration.ofHours(10)), 600));
        IrradianceSeries mitZukunft = new IrradianceSeries(
                irradiance.next.orElseThrow().past(), zukunft, NOW);
        irradiance.next = Optional.of(mitZukunft);
        ForecastService forecast = forecastService();
        ProfileLearningService service =
                new ProfileLearningService(irradiance, samples, profiles, forecast, CLOCK, 21);

        assertTrue(forecast.currentForecast().isEmpty(), "vor dem Lernen noch nichts gerechnet");
        service.learn();

        assertTrue(forecast.currentForecast().isPresent(), "Lernen stoesst den Refresh an");
        assertEquals(Confidence.LEARNED, forecast.currentForecast().orElseThrow().confidence());
    }
}
