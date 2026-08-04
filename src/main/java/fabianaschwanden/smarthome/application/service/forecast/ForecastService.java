package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.ChargeRecommendation;
import fabianaschwanden.smarthome.domain.model.forecast.ConsumptionBaseline;
import fabianaschwanden.smarthome.domain.model.forecast.ConsumptionSample;
import fabianaschwanden.smarthome.domain.model.forecast.IrradianceSeries;
import fabianaschwanden.smarthome.domain.model.forecast.PlantProfile;
import fabianaschwanden.smarthome.domain.model.forecast.PvForecast;
import fabianaschwanden.smarthome.domain.model.forecast.SurplusWindow;
import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
import fabianaschwanden.smarthome.domain.port.in.forecast.PvForecastQuery;
import fabianaschwanden.smarthome.domain.port.in.forecast.SurplusQuery;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySampleRepository;
import fabianaschwanden.smarthome.domain.port.out.forecast.IrradianceGateway;
import fabianaschwanden.smarthome.domain.port.out.forecast.PlantProfileRepository;
import fabianaschwanden.smarthome.domain.service.forecast.PlantProfileLearner;
import fabianaschwanden.smarthome.domain.service.forecast.PvForecaster;
import fabianaschwanden.smarthome.domain.service.forecast.SurplusPlanner;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Hält die aktuelle PV-Prognose und die daraus abgeleiteten Überschussfenster im
 * Speicher und rechnet sie periodisch neu.
 *
 * <p>Bewusst im RAM statt in der Datenbank: Die Prognose ist ein abgeleiteter Wert, der
 * bei jedem Refresh ohnehin neu entsteht. Nach einem Neustart ist sie einmal leer, bis
 * der erste Abruf durch ist – das ist ehrlicher als ein persistierter Stand, dessen Alter
 * niemand sieht.
 *
 * <p>Fällt die Strahlungsquelle aus, bleibt die letzte Prognose stehen (SPEC §6). Sie
 * trägt ihren Rechenzeitpunkt mit, damit im UI sichtbar wird, dass die Zahlen altern.
 */
@ApplicationScoped
public class ForecastService implements PvForecastQuery, SurplusQuery {

    private static final Logger LOG = Logger.getLogger(ForecastService.class);

    private final IrradianceGateway irradiance;
    private final PlantProfileRepository profiles;
    private final EnergySampleRepository samples;
    private final PvForecaster forecaster;
    private final SurplusPlanner planner;
    private final PlantProfileLearner learner;
    private final Clock clock;
    private final ZoneId zone;
    private final int baselineWindowDays;
    private final double minWatt;
    private final Duration minDuration;
    private final double coldStartKwp;

    private volatile PvForecast forecast;
    private volatile ConsumptionBaseline baseline;
    private volatile List<SurplusWindow> windows = List.of();

    @Inject
    public ForecastService(
            IrradianceGateway irradiance,
            PlantProfileRepository profiles,
            EnergySampleRepository samples,
            @ConfigProperty(name = "forecast.learning.window-days", defaultValue = "21") int windowDays,
            @ConfigProperty(name = "forecast.surplus.min-watt", defaultValue = "500") double minWatt,
            @ConfigProperty(name = "forecast.surplus.min-duration", defaultValue = "2h") Duration minDuration,
            @ConfigProperty(name = "forecast.plant.kwp", defaultValue = "10") double coldStartKwp) {
        this(irradiance, profiles, samples, Clock.systemDefaultZone(),
                windowDays, minWatt, minDuration, coldStartKwp);
    }

    // Sichtbar fürs Testen: erlaubt eine feste Uhr und damit deterministische Läufe.
    ForecastService(
            IrradianceGateway irradiance,
            PlantProfileRepository profiles,
            EnergySampleRepository samples,
            Clock clock,
            int baselineWindowDays,
            double minWatt,
            Duration minDuration,
            double coldStartKwp) {
        this.irradiance = irradiance;
        this.profiles = profiles;
        this.samples = samples;
        this.clock = clock;
        this.zone = clock.getZone();
        this.baselineWindowDays = baselineWindowDays;
        this.minWatt = minWatt;
        this.minDuration = minDuration;
        this.coldStartKwp = coldStartKwp;
        this.forecaster = new PvForecaster();
        this.planner = new SurplusPlanner();
        this.learner = new PlantProfileLearner();
    }

    /** Beim Start einmal rechnen, damit das Dashboard nicht bis zum ersten Takt leer bleibt. */
    void onStart(@Observes StartupEvent event) {
        refresh();
    }

    @Scheduled(every = "{forecast.refresh-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledRefresh() {
        refresh();
    }

    /** Strahlung holen, Prognose und Fenster neu rechnen. Fehler degradieren, sie werfen nicht. */
    public void refresh() {
        try {
            Optional<IrradianceSeries> series = irradiance.fetch();
            if (series.isEmpty() || series.get().forecast().isEmpty()) {
                // Bewusst KEIN Zurücksetzen: die letzte Prognose ist besser als keine,
                // ihr Alter steht in computedAt (SPEC §6).
                LOG.info("Keine Strahlungsdaten – bisherige Prognose bleibt stehen");
                return;
            }
            PlantProfile profile = profileOrColdStart();
            Instant now = clock.instant();
            PvForecast computed = forecaster.forecast(profile, series.get().forecast(), zone, now);
            ConsumptionBaseline computedBaseline = planner.baseline(consumptionSamples(now));
            List<SurplusWindow> computedWindows =
                    planner.windows(computed, computedBaseline, zone, minWatt, minDuration);

            this.forecast = computed;
            this.baseline = computedBaseline;
            this.windows = computedWindows;
            LOG.infof("Prognose neu gerechnet: heute %.1f kWh, morgen %.1f kWh, %d Fenster (%s)",
                    computed.todayKwh(), computed.tomorrowKwh(), computedWindows.size(),
                    computed.confidence());
        } catch (Exception e) {
            LOG.warnf("Prognose konnte nicht gerechnet werden: %s", e.getMessage());
        }
    }

    /** Gelerntes Profil, sonst der grobe Fallback aus der Nennleistung. */
    private PlantProfile profileOrColdStart() {
        return profiles.load().orElseGet(() -> {
            LOG.info("Noch kein gelerntes Anlagenprofil – Cold-Start-Fallback");
            return learner.coldStart(coldStartKwp, clock.instant());
        });
    }

    /**
     * Verdichtet die Roh-Messpunkte des Lernfensters zu einem Verbrauchswert je Stunde.
     * Mehrere Messpunkte pro Stunde werden gemittelt – die Baseline arbeitet auf
     * Stundenwerten, nicht auf dem 10-Sekunden-Takt der Zeitreihe.
     */
    private List<ConsumptionSample> consumptionSamples(Instant now) {
        Instant from = now.minus(Duration.ofDays(baselineWindowDays));
        List<EnergySample> raw = samples.between(from, now);
        if (raw.isEmpty()) {
            return List.of();
        }
        // Schlüssel: Stunde seit Epoche – gruppiert Messpunkte derselben Stunde.
        var sumPerHour = new java.util.LinkedHashMap<Long, double[]>();
        for (EnergySample sample : raw) {
            long hourKey = sample.timestamp().getEpochSecond() / 3600;
            double[] acc = sumPerHour.computeIfAbsent(hourKey, k -> new double[2]);
            acc[0] += sample.consumptionWatt();
            acc[1] += 1;
        }
        List<ConsumptionSample> result = new ArrayList<>(sumPerHour.size());
        sumPerHour.forEach((hourKey, acc) -> {
            ZonedDateTime local = Instant.ofEpochSecond(hourKey * 3600).atZone(zone);
            result.add(new ConsumptionSample(
                    local.getHour(),
                    ConsumptionBaseline.isWeekend(local.getDayOfWeek()),
                    Math.max(0, acc[0] / acc[1])));
        });
        return result;
    }

    @Override
    public Optional<PvForecast> currentForecast() {
        return Optional.ofNullable(forecast);
    }

    @Override
    public List<SurplusWindow> windows() {
        return windows;
    }

    @Override
    public Optional<ChargeRecommendation> recommendation() {
        PvForecast current = forecast;
        if (current == null) {
            return Optional.empty();
        }
        return planner.best(windows).map(window -> new ChargeRecommendation(window, current.confidence()));
    }

    @Override
    public Optional<ConsumptionBaseline> baseline() {
        return Optional.ofNullable(baseline);
    }
}
