package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;
import fabianaschwanden.smarthome.domain.model.forecast.AutoApplyOutcome;
import fabianaschwanden.smarthome.domain.model.forecast.AutoApplyState;
import fabianaschwanden.smarthome.domain.port.in.forecast.ApplyRecommendation;
import fabianaschwanden.smarthome.domain.port.in.forecast.ForecastAccuracyQuery;
import fabianaschwanden.smarthome.domain.port.in.forecast.ManageAutoApply;
import fabianaschwanden.smarthome.domain.port.in.forecast.NoRecommendationAvailable;
import fabianaschwanden.smarthome.domain.port.out.forecast.AutoApplyStateRepository;
import fabianaschwanden.smarthome.domain.service.forecast.ForecastTrust;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Übernimmt die Ladeempfehlung ohne Nutzeraktion – aber nur, wenn die Prognose sich
 * vorher bewährt hat.
 *
 * <p><b>Die Genauigkeitsmessung ist hier der Schutzschalter</b> (F1): Geschaltet wird
 * erst, wenn genug Tage ausgewertet sind <em>und</em> der mittlere Fehler unter der
 * Schwelle liegt. Ohne diese Hürde würde die Automatik ausgerechnet direkt nach der
 * Inbetriebnahme losschalten – dann, wenn sie am wenigsten über die Anlage weiss.
 *
 * <p>Der Tag des Laufs wird auch dann festgehalten, wenn nichts übernommen wurde. Sonst
 * liefe die Automatik nach einem Neustart am selben Tag erneut und legte einen zweiten
 * Zeitplan an.
 */
@ApplicationScoped
public class AutoApplyService implements ManageAutoApply {

    private static final Logger LOG = Logger.getLogger(AutoApplyService.class);

    private final AutoApplyStateRepository repository;
    private final ForecastAccuracyQuery accuracy;
    private final ApplyRecommendation applyRecommendation;
    private final ForecastTrust trust = new ForecastTrust();
    private final Clock clock;
    private final int accuracyDays;
    private final int minRatedDays;
    private final double maxMape;

    @Inject
    public AutoApplyService(
            AutoApplyStateRepository repository,
            ForecastAccuracyQuery accuracy,
            ApplyRecommendation applyRecommendation,
            @ConfigProperty(name = "energy.history.zone", defaultValue = "Europe/Zurich") String zone,
            @ConfigProperty(name = "forecast.auto-apply.accuracy-days") int accuracyDays,
            @ConfigProperty(name = "forecast.auto-apply.min-rated-days") int minRatedDays,
            @ConfigProperty(name = "forecast.auto-apply.max-mape") double maxMape) {
        this(repository, accuracy, applyRecommendation, Clock.system(ZoneId.of(zone)),
                accuracyDays, minRatedDays, maxMape);
    }

    // Sichtbar fürs Testen: feste Uhr, deterministische Tagesgrenzen.
    AutoApplyService(
            AutoApplyStateRepository repository,
            ForecastAccuracyQuery accuracy,
            ApplyRecommendation applyRecommendation,
            Clock clock,
            int accuracyDays,
            int minRatedDays,
            double maxMape) {
        this.repository = repository;
        this.accuracy = accuracy;
        this.applyRecommendation = applyRecommendation;
        this.clock = clock;
        this.accuracyDays = accuracyDays;
        this.minRatedDays = minRatedDays;
        this.maxMape = maxMape;
    }

    @Override
    public AutoApplyState state() {
        return repository.load();
    }

    @Override
    public AutoApplyState setEnabled(boolean enabled) {
        AutoApplyState updated = repository.load().withEnabled(enabled);
        repository.save(updated);
        LOG.infof("Lade-Automatik %s", enabled ? "eingeschaltet" : "ausgeschaltet");
        return updated;
    }

    @Scheduled(cron = "{forecast.auto-apply.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledRun() {
        run();
    }

    /** Ein Lauf: prüfen, ob geschaltet werden darf, und gegebenenfalls übernehmen. */
    void run() {
        AutoApplyState state = repository.load();
        LocalDate today = LocalDate.now(clock);
        if (!state.enabled() || state.ranOn(today)) {
            return;
        }

        Optional<AutoApplyOutcome> objection =
                trust.objection(accuracy.accuracy(accuracyDays), minRatedDays, maxMape);
        if (objection.isPresent()) {
            String detail = describe(objection.get());
            repository.save(state.ranOn(today, objection.get(), detail));
            LOG.infof("Lade-Automatik hat nicht geschaltet: %s", detail);
            return;
        }

        try {
            BatterySchedule schedule = applyRecommendation.apply();
            String detail = "Ladefenster ab " + schedule.fireAt() + " übernommen";
            repository.save(state.ranOn(today, AutoApplyOutcome.APPLIED, detail));
            LOG.infof("Lade-Automatik: %s", detail);
        } catch (NoRecommendationAvailable e) {
            // Kein Fehler, sondern ein Fachfall: An einem trueben Tag gibt es nichts
            // zu uebernehmen.
            repository.save(state.ranOn(today, AutoApplyOutcome.NO_RECOMMENDATION,
                    "Heute kein Überschussfenster"));
        }
    }

    private String describe(AutoApplyOutcome outcome) {
        return switch (outcome) {
            case NOT_ENOUGH_DATA -> "Noch zu wenige ausgewertete Tage (mindestens " + minRatedDays + ")";
            case FORECAST_UNRELIABLE -> "Prognose lag zuletzt im Mittel über " + (int) maxMape + " % daneben";
            default -> "";
        };
    }
}
