package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.domain.model.energy.EnergyBucket;
import fabianaschwanden.smarthome.domain.model.energy.HistoryRange;
import fabianaschwanden.smarthome.domain.model.forecast.AccuracyHistory;
import fabianaschwanden.smarthome.domain.model.forecast.ForecastAccuracy;
import fabianaschwanden.smarthome.domain.model.forecast.PvForecast;
import fabianaschwanden.smarthome.domain.port.in.energy.EnergyHistoryQuery;
import fabianaschwanden.smarthome.domain.port.in.forecast.ForecastAccuracyQuery;
import fabianaschwanden.smarthome.domain.port.in.forecast.PvForecastQuery;
import fabianaschwanden.smarthome.domain.port.out.forecast.ForecastAccuracyRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Führt Buch darüber, wie gut die Prognose war: Am Morgen wird festgehalten, was der
 * Tag bringen soll, nach Tagesende, was er gebracht hat.
 *
 * <p><b>Warum der Morgen und nicht Mitternacht:</b> Eine Prognose ist nur etwas wert,
 * wenn sie vor der Sache da ist – aber die Zahl, nach der man tatsächlich handelt, ist
 * die vom Morgen. Sie wird deshalb einmal je Tag festgeschrieben und danach nicht mehr
 * angefasst; sonst würde sich die Prognose im Lauf des Tages an die Wirklichkeit
 * anschmiegen und die Auswertung schöner aussehen lassen, als sie ist.
 *
 * <p>Der Ist-Wert kommt aus derselben Aggregation wie das Energie-Diagramm
 * ({@link EnergyHistoryQuery}) – so kann die Genauigkeitsanzeige nicht von dem
 * abweichen, was das Dashboard für denselben Tag zeigt.
 */
@ApplicationScoped
public class ForecastAccuracyService implements ForecastAccuracyQuery {

    private static final Logger LOG = Logger.getLogger(ForecastAccuracyService.class);

    /** Ohne Angabe zeigt die Auswertung so viele Tage. */
    public static final int DEFAULT_DAYS = 14;

    private final ForecastAccuracyRepository repository;
    private final PvForecastQuery forecasts;
    private final EnergyHistoryQuery history;
    private final Clock clock;
    private final ZoneId zone;

    @Inject
    public ForecastAccuracyService(
            ForecastAccuracyRepository repository,
            PvForecastQuery forecasts,
            EnergyHistoryQuery history,
            @ConfigProperty(name = "energy.history.zone", defaultValue = "Europe/Zurich") String zone) {
        this(repository, forecasts, history, Clock.system(ZoneId.of(zone)));
    }

    // Sichtbar fürs Testen: feste Uhr, deterministische Tagesgrenzen.
    ForecastAccuracyService(
            ForecastAccuracyRepository repository,
            PvForecastQuery forecasts,
            EnergyHistoryQuery history,
            Clock clock) {
        this.repository = repository;
        this.forecasts = forecasts;
        this.history = history;
        this.clock = clock;
        this.zone = clock.getZone();
    }

    @Override
    public AccuracyHistory accuracy(int days) {
        return AccuracyHistory.of(repository.latest(days <= 0 ? DEFAULT_DAYS : days));
    }

    @Scheduled(cron = "{forecast.accuracy.record-cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledRecord() {
        recordToday();
    }

    @Scheduled(cron = "{forecast.accuracy.settle-cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledSettle() {
        settle(today().minusDays(1));
    }

    /** Schreibt die Prognose für heute fest – einmal, beim ersten Lauf des Tages. */
    void recordToday() {
        LocalDate today = today();
        if (repository.byDate(today).isPresent()) {
            // Schon festgehalten. Ein zweiter Lauf darf die Zahl nicht nachbessern.
            return;
        }
        Optional<PvForecast> forecast = forecasts.currentForecast();
        if (forecast.isEmpty()) {
            LOG.info("Keine Prognose vorhanden – für heute wird keine Genauigkeit erfasst.");
            return;
        }
        repository.save(ForecastAccuracy.predicted(today, forecast.get().todayKwh()));
        LOG.infof("Prognose für %s festgehalten: %.1f kWh", today, forecast.get().todayKwh());
    }

    /** Trägt den Ist-Wert eines abgeschlossenen Tages nach. */
    void settle(LocalDate date) {
        Optional<ForecastAccuracy> entry = repository.byDate(date);
        if (entry.isEmpty()) {
            LOG.infof("Für %s wurde keine Prognose festgehalten – nichts nachzutragen.", date);
            return;
        }
        if (entry.get().isSettled()) {
            return;
        }
        Optional<Double> actual = actualKwh(date);
        if (actual.isEmpty()) {
            // Ohne Messpunkte gibt es keinen Ist-Wert. 0 einzutragen hiesse zu behaupten,
            // die Anlage habe nichts produziert - dabei fehlen nur die Daten.
            LOG.warnf("Keine Messwerte für %s – Ist-Wert bleibt offen.", date);
            return;
        }
        repository.save(entry.get().settledWith(actual.get()));
        LOG.infof("Ist-Wert für %s nachgetragen: %.1f kWh (Prognose %.1f kWh)",
                date, actual.get(), entry.get().forecastKwh());
    }

    /** Tagesertrag aus der Energie-Historie; leer, wenn für den Tag kein Bucket vorliegt. */
    private Optional<Double> actualKwh(LocalDate date) {
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        List<EnergyBucket> buckets = history.history(HistoryRange.MONTH).buckets();
        return buckets.stream()
                .filter(bucket -> bucket.start().equals(dayStart))
                .findFirst()
                .map(EnergyBucket::pvKwh);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
