package fabianaschwanden.smarthome.application.service.charging;

import fabianaschwanden.smarthome.domain.model.battery.BatteryControl;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.charging.ChargingSession;
import fabianaschwanden.smarthome.domain.port.in.battery.ControlBattery;
import fabianaschwanden.smarthome.domain.port.in.charging.ChargingSessionQuery;
import fabianaschwanden.smarthome.domain.port.out.charging.ChargingSessionRepository;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySampleRepository;
import fabianaschwanden.smarthome.domain.service.charging.ChargingEnergyEstimator;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Schreibt mit, wann die Batterie geladen hat, und schätzt die dabei bezogene Energie.
 *
 * <p>Beobachtet den Relais-Zustand, statt sich vom Schaltbefehl benachrichtigen zu lassen:
 * So werden auch Ladevorgänge erfasst, die <b>direkt am SMARTFOX</b> gestartet wurden –
 * und genau das kommt vor. Der Zustand wird ohnehin periodisch mit dem Gerät abgeglichen.
 *
 * <p>Geschätzt wird erst am Ende, aus den Messpunkten, die bis dahin ohnehin aufgezeichnet
 * wurden. Während des Ladens muss der Dienst nichts mitrechnen und kann deshalb auch nichts
 * verlieren.
 */
@ApplicationScoped
public class ChargingSessionRecorder implements ChargingSessionQuery {

    private static final Logger LOG = Logger.getLogger(ChargingSessionRecorder.class);

    private final ControlBattery battery;
    private final ChargingSessionRepository sessions;
    private final EnergySampleRepository samples;
    private final ChargingEnergyEstimator estimator = new ChargingEnergyEstimator();
    private final Duration settleTime;
    private final Duration baselineWindow;

    @Inject
    public ChargingSessionRecorder(
            ControlBattery battery,
            ChargingSessionRepository sessions,
            EnergySampleRepository samples,
            @ConfigProperty(name = "battery.charging.settle-time") Duration settleTime,
            @ConfigProperty(name = "battery.charging.baseline-window") Duration baselineWindow) {
        this.battery = battery;
        this.sessions = sessions;
        this.samples = samples;
        this.settleTime = settleTime;
        this.baselineWindow = baselineWindow;
    }

    @Override
    public List<ChargingSession> recentSessions(int limit) {
        return sessions.latest(limit);
    }

    @Scheduled(every = "{battery.charging.tick-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        BatteryControl control = battery.status();
        boolean charging = control.desiredState() == RelayState.ON;
        Optional<Instant> open = sessions.openStart();

        if (charging && open.isEmpty()) {
            // changedAt statt "jetzt": Es zaehlt, wann geschaltet wurde, nicht wann der
            // Dienst hingeschaut hat.
            sessions.open(control.changedAt());
            LOG.infof("Ladevorgang begonnen: %s", control.changedAt());
        } else if (!charging && open.isPresent()) {
            finish(open.get(), control.changedAt());
        }
    }

    private void finish(Instant startedAt, Instant endedAt) {
        Instant from = startedAt.minus(baselineWindow);
        Optional<ChargingSession> estimate = estimator.estimate(
                samples.between(from, endedAt), startedAt, endedAt, settleTime, baselineWindow);

        if (estimate.isEmpty()) {
            // Ohne Vergleichswerte gibt es keine Zahl. Eine 0 einzutragen saehe aus wie
            // "nicht geladen" - der Vorgang wird lieber verworfen als erfunden.
            sessions.discardOpen();
            LOG.infof("Ladevorgang %s bis %s: zu wenige Messpunkte, nicht geschaetzt", startedAt, endedAt);
            return;
        }
        sessions.close(estimate.get());
        LOG.infof("Ladevorgang beendet: %.2f kWh bei %.0f W ueber %d min",
                estimate.get().energyKwh(), estimate.get().watt(), estimate.get().duration().toMinutes());
    }
}
