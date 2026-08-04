package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
import fabianaschwanden.smarthome.domain.model.forecast.IrradiancePoint;
import fabianaschwanden.smarthome.domain.model.forecast.IrradianceSeries;
import fabianaschwanden.smarthome.domain.model.forecast.PlantProfile;
import fabianaschwanden.smarthome.domain.model.forecast.PvGtiSample;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySampleRepository;
import fabianaschwanden.smarthome.domain.port.out.forecast.IrradianceGateway;
import fabianaschwanden.smarthome.domain.port.out.forecast.PlantProfileRepository;
import fabianaschwanden.smarthome.domain.service.forecast.PlantProfileLearner;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lernt nachts das Anlagenprofil neu: Ist-Strahlung gegen Ist-Leistung derselben Stunde.
 *
 * <p>Nachts, weil dann keine Messwerte mehr dazukommen und der Tag vollständig ist – und
 * weil ein Lernlauf die Prognose ändert, was mitten am Tag verwirrend wäre.
 */
@ApplicationScoped
public class ProfileLearningService {

    private static final Logger LOG = Logger.getLogger(ProfileLearningService.class);

    private final IrradianceGateway irradiance;
    private final EnergySampleRepository samples;
    private final PlantProfileRepository profiles;
    private final ForecastService forecastService;
    private final PlantProfileLearner learner = new PlantProfileLearner();
    private final Clock clock;
    private final ZoneId zone;
    private final int windowDays;

    @Inject
    public ProfileLearningService(
            IrradianceGateway irradiance,
            EnergySampleRepository samples,
            PlantProfileRepository profiles,
            ForecastService forecastService,
            @ConfigProperty(name = "forecast.learning.window-days", defaultValue = "21") int windowDays) {
        this(irradiance, samples, profiles, forecastService, Clock.systemDefaultZone(), windowDays);
    }

    // Sichtbar fürs Testen: feste Uhr, deterministische Läufe.
    ProfileLearningService(
            IrradianceGateway irradiance,
            EnergySampleRepository samples,
            PlantProfileRepository profiles,
            ForecastService forecastService,
            Clock clock,
            int windowDays) {
        this.irradiance = irradiance;
        this.samples = samples;
        this.profiles = profiles;
        this.forecastService = forecastService;
        this.clock = clock;
        this.zone = clock.getZone();
        this.windowDays = windowDays;
    }

    @Scheduled(cron = "{forecast.learning.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledLearning() {
        learn();
    }

    /**
     * Ein Lernlauf. Schlägt er fehl oder reichen die Daten nicht, bleibt das bisherige
     * Profil unangetastet – ein halb gelerntes Profil wäre schlechter als ein altes.
     */
    public void learn() {
        try {
            Optional<IrradianceSeries> series = irradiance.fetch();
            if (series.isEmpty() || series.get().past().isEmpty()) {
                LOG.info("Lernen übersprungen: keine Ist-Strahlung verfügbar");
                return;
            }
            List<PvGtiSample> pairs = pair(series.get().past());
            if (pairs.isEmpty()) {
                LOG.info("Lernen übersprungen: keine überlappenden Stunden von Strahlung und Leistung");
                return;
            }
            Optional<PlantProfile> learned = learner.learn(pairs, clock.instant());
            if (learned.isEmpty()) {
                LOG.infof("Lernen ohne Ergebnis: %d Paare, aber kein Slot mit genug Punkten", pairs.size());
                return;
            }
            profiles.save(learned.get());
            LOG.infof("Anlagenprofil neu gelernt aus %d Paaren (max %.0f W)",
                    pairs.size(), learned.get().maxObservedPvWatt());
            // Die Prognose hängt am Profil – sofort neu rechnen, sonst zeigt das UI bis
            // zum nächsten Refresh Zahlen aus dem alten Profil.
            forecastService.refresh();
        } catch (Exception e) {
            LOG.warnf("Lernlauf fehlgeschlagen, bisheriges Profil bleibt: %s", e.getMessage());
        }
    }

    /**
     * Bildet Paare (Stunden-Slot, Ø-Leistung, Ist-Strahlung) aus beiden Quellen.
     *
     * <p>Gepaart wird über die volle Stunde: die Zeitreihe liefert Messpunkte im
     * Sekundentakt, die Strahlung einen Wert je Stunde. Stunden ohne Gegenstück fallen
     * raus – ein Paar mit nur einer Hälfte wäre kein Lernwert.
     */
    private List<PvGtiSample> pair(List<IrradiancePoint> pastIrradiance) {
        Instant from = clock.instant().minus(Duration.ofDays(windowDays));
        Map<Long, double[]> pvPerHour = new LinkedHashMap<>();
        for (EnergySample sample : samples.between(from, clock.instant())) {
            long hourKey = sample.timestamp().getEpochSecond() / 3600;
            double[] acc = pvPerHour.computeIfAbsent(hourKey, k -> new double[2]);
            acc[0] += sample.pvWatt();
            acc[1] += 1;
        }
        List<PvGtiSample> pairs = new ArrayList<>();
        for (IrradiancePoint point : pastIrradiance) {
            long hourKey = point.hour().getEpochSecond() / 3600;
            double[] acc = pvPerHour.get(hourKey);
            if (acc == null || acc[1] == 0) {
                continue;
            }
            int hourOfDay = point.hour().atZone(zone).getHour();
            pairs.add(new PvGtiSample(hourOfDay, Math.max(0, acc[0] / acc[1]), point.gtiWattPerSqm()));
        }
        return pairs;
    }
}
