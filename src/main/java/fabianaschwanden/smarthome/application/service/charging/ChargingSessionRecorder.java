package fabianaschwanden.smarthome.application.service.charging;

import fabianaschwanden.smarthome.domain.model.battery.BatteryControl;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.charging.ChargingSession;
import fabianaschwanden.smarthome.domain.model.charging.OpenChargingSession;
import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

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
 *
 * <p><b>Gegenmessung:</b> Nach {@code verify-after} wird einmal je Ladevorgang kurz
 * abgeschaltet und wieder eingeschaltet. Der Verbrauch fällt dabei um die Ladeleistung –
 * eine zweite, unabhängige Messung, und zwar im eingeschwungenen Zustand, während die
 * erste unmittelbar nach dem Einschalten fällt, wo das Ladegerät noch anläuft. Weichen
 * beide stark ab, hat vermutlich eine andere Last mitgeschaltet.
 *
 * <p>Das ist ein echter Eingriff an der Anlage: Das Relais schaltet zweimal zusätzlich je
 * Ladevorgang, und für die Dauer der Pause wird nicht geladen. Deshalb abschaltbar
 * ({@code verify-enabled}) und nur im Manuell-Modus – im Automatik-Modus gehört das Relais
 * dem SMARTFOX, und ein Eingriff von aussen würde gegen dessen Regelung arbeiten.
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
    private final boolean verifyEnabled;
    private final Duration verifyAfter;
    private final Duration verifyPause;
    private final Clock clock;

    @Inject
    public ChargingSessionRecorder(
            ControlBattery battery,
            ChargingSessionRepository sessions,
            EnergySampleRepository samples,
            @ConfigProperty(name = "battery.charging.settle-time") Duration settleTime,
            @ConfigProperty(name = "battery.charging.baseline-window") Duration baselineWindow,
            @ConfigProperty(name = "battery.charging.verify-enabled") boolean verifyEnabled,
            @ConfigProperty(name = "battery.charging.verify-after") Duration verifyAfter,
            @ConfigProperty(name = "battery.charging.verify-pause") Duration verifyPause) {
        this(battery, sessions, samples, settleTime, baselineWindow, verifyEnabled, verifyAfter,
                verifyPause, Clock.systemUTC());
    }

    // Sichtbar fuers Testen: feste Uhr. Ohne sie liess sich die Faelligkeit nicht pruefen -
    // und genau dort steckte ein Fehler, den die Tests deshalb nicht sahen.
    ChargingSessionRecorder(
            ControlBattery battery,
            ChargingSessionRepository sessions,
            EnergySampleRepository samples,
            Duration settleTime,
            Duration baselineWindow,
            boolean verifyEnabled,
            Duration verifyAfter,
            Duration verifyPause,
            Clock clock) {
        this.clock = clock;
        this.battery = battery;
        this.sessions = sessions;
        this.samples = samples;
        this.settleTime = settleTime;
        this.baselineWindow = baselineWindow;
        this.verifyEnabled = verifyEnabled;
        this.verifyAfter = verifyAfter;
        this.verifyPause = verifyPause;
    }

    @Override
    public List<ChargingSession> recentSessions(int limit) {
        return sessions.latest(limit);
    }

    @Scheduled(every = "{battery.charging.tick-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        BatteryControl control = battery.status();
        boolean relayOn = control.desiredState() == RelayState.ON;
        Optional<OpenChargingSession> open = sessions.open();

        // Die Pause der Gegenmessung zuerst: In ihr ist das Relais AUS, obwohl der
        // Ladevorgang laeuft. Ohne diesen Zweig hielte der Dienst sie fuer ein Ende.
        if (open.isPresent() && open.get().isPaused()) {
            resumeIfDue(open.get(), control);
            return;
        }

        if (relayOn && open.isEmpty()) {
            // changedAt statt "jetzt": Es zaehlt, wann geschaltet wurde, nicht wann der
            // Dienst hingeschaut hat.
            sessions.open(control.changedAt());
            LOG.infof("Ladevorgang begonnen: %s", control.changedAt());
        } else if (relayOn && open.isPresent()) {
            verifyIfDue(open.get(), control);
        } else if (!relayOn && open.isPresent()) {
            finish(open.get(), control.changedAt());
        }
    }

    /** Schaltet fuer die Gegenmessung kurz ab - einmal je Ladevorgang. */
    private void verifyIfDue(OpenChargingSession open, BatteryControl control) {
        if (!verifyEnabled || open.verificationDone() || control.mode() != ControlMode.MANUAL) {
            return;
        }
        // clock.instant(), NICHT control.changedAt(): Letzteres ist der Zeitpunkt der
        // letzten Relais-Aenderung und steht still, solange nichts geschaltet wird. Genau
        // damit hat sich die Gegenmessung selbst blockiert - nach einem Neustart liegt
        // changedAt hinter dem Sessionbeginn, und die Faelligkeit trat nie ein.
        if (clock.instant().isBefore(open.startedAt().plus(verifyAfter))) {
            return;
        }
        Instant pausedAt = clock.instant();
        battery.switchRelay(RelayState.OFF);
        sessions.updateOpen(open.verifyStarted(pausedAt));
        LOG.infof("Gegenmessung: Relais fuer %s abgeschaltet", verifyPause);
    }

    /**
     * Beendet die Pause. Laeuft auch nach einem Neustart an – deshalb steht der Stand in
     * der Datenbank: Sonst bliebe die Anlage ausgeschaltet zurueck.
     */
    private void resumeIfDue(OpenChargingSession open, BatteryControl control) {
        Instant pausedAt = open.verifyStartedAt().orElseThrow();
        if (clock.instant().isBefore(pausedAt.plus(verifyPause))) {
            return;
        }
        Instant resumedAt = clock.instant();
        if (control.desiredState() != RelayState.ON) {
            battery.switchRelay(RelayState.ON);
        }
        sessions.updateOpen(open.verifyEnded(resumedAt));
        LOG.infof("Gegenmessung beendet, Relais wieder ein (Pause %s)",
                Duration.between(pausedAt, resumedAt));
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private void finish(OpenChargingSession open, Instant endedAt) {
        Instant startedAt = open.startedAt();
        // Nur die Fenster laden, die die Rechnung braucht - nicht den ganzen Ladevorgang.
        // Bei einer Batterie, die tagelang am Netz haengt, waeren das Zehntausende
        // Messpunkte (alle 10 s), und alle davon nur, um zwei Mediane zu bilden.
        Instant stepEnd = startedAt.plus(settleTime).plus(baselineWindow);
        List<EnergySample> stepWindow = samples.between(startedAt.minus(baselineWindow), stepEnd);
        Optional<ChargingSession> step = estimator.estimate(
                stepWindow, startedAt, min(stepEnd, endedAt), settleTime, baselineWindow);

        if (step.isEmpty()) {
            // Ohne Vergleichswerte gibt es keine Zahl. Eine 0 einzutragen saehe aus wie
            // "nicht geladen" - der Vorgang wird lieber verworfen als erfunden.
            sessions.discardOpen();
            LOG.infof("Ladevorgang %s bis %s: zu wenige Messpunkte, nicht geschaetzt", startedAt, endedAt);
            return;
        }

        OptionalDouble verified = OptionalDouble.empty();
        Duration pausedFor = Duration.ZERO;
        if (open.verifyStartedAt().isPresent() && open.verifyEndedAt().isPresent()) {
            Instant pausedAt = open.verifyStartedAt().get();
            Instant resumedAt = open.verifyEndedAt().get();
            List<EnergySample> pauseWindow =
                    samples.between(pausedAt.minus(baselineWindow), resumedAt.plus(settleTime));
            verified = estimator.verify(pauseWindow, pausedAt, resumedAt, settleTime, baselineWindow);
            pausedFor = Duration.between(pausedAt, resumedAt);
        }

        ChargingSession session = estimator.session(
                startedAt, endedAt, step.get().watt(), verified, pausedFor);
        sessions.close(session);
        LOG.infof("Ladevorgang beendet: %.2f kWh (Sprung %.0f W, Gegenmessung %s) ueber %d min",
                session.energyKwh(), session.watt(),
                verified.isPresent() ? String.format("%.0f W", verified.getAsDouble()) : "keine",
                session.duration().toMinutes());
    }
}
