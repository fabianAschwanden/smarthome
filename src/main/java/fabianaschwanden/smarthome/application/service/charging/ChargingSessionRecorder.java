package fabianaschwanden.smarthome.application.service.charging;

import fabianaschwanden.smarthome.domain.model.battery.BatteryControl;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.charging.ChargingSession;
import fabianaschwanden.smarthome.domain.model.charging.OpenChargingSession;
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

    private void finish(OpenChargingSession open, Instant endedAt) {
        Instant startedAt = open.startedAt();

        // Lange Fenster, und der Median aus der Datenbank statt geladener Zeilen.
        //
        // Die erste Fassung verglich zwei Minuten vor dem Einschalten mit zwei Minuten
        // danach. Am 29.08.2026 lagen in diesen zwei Minuten zufaellig 2572 W statt der
        // sonst typischen 1981 W - eine gewoehnliche Haushaltsspitze. Die Differenz wurde
        // negativ, und negativ heisst 0: Ein Ladevorgang ueber viereinhalb Stunden stand
        // mit 0 kWh in der Liste. Ein Haus schwankt um +-1000 W, also in derselben
        // Groessenordnung wie die gesuchte Ladeleistung; zwei Minuten sind dagegen kein
        // Mass. Ueber 30 Minuten Vergleich und den ganzen Ladevorgang gerechnet ergaben
        // dieselben Daten rund 1600 W.
        OptionalDouble before =
                samples.medianConsumptionBetween(startedAt.minus(baselineWindow), startedAt);
        OptionalDouble during =
                samples.medianConsumptionBetween(startedAt.plus(settleTime), endedAt);

        if (before.isEmpty() || during.isEmpty()) {
            // Ohne Vergleichswerte gibt es keine Zahl. Eine 0 einzutragen saehe aus wie
            // "nicht geladen" - der Vorgang wird lieber verworfen als erfunden.
            sessions.discardOpen();
            LOG.infof("Ladevorgang %s bis %s: zu wenige Messpunkte, nicht geschaetzt", startedAt, endedAt);
            return;
        }

        double watt = Math.max(0, during.getAsDouble() - before.getAsDouble());
        OptionalDouble verified = verifiedWatt(open);
        ChargingSession session = estimator.session(startedAt, endedAt, watt, verified, pausedFor(open));
        sessions.close(session);
        LOG.infof("Ladevorgang beendet: %.2f kWh (%.0f W ueber %d min; vorher %.0f W, "
                        + "waehrend %.0f W; Gegenmessung %s)",
                session.energyKwh(), session.watt(), session.duration().toMinutes(),
                before.getAsDouble(), during.getAsDouble(),
                verified.isPresent() ? String.format("%.0f W", verified.getAsDouble()) : "keine");
    }

    /**
     * Die Gegenmessung aus der Pause - nur noch zum Vergleich, nicht als Grundlage der
     * Energie.
     *
     * <p>Zwei Minuten Pause sind demselben Rauschen ausgesetzt wie die alten kurzen
     * Fenster: Schaltet in dieser Zeit zufaellig ein Backofen, misst man ihn statt das
     * Ladegeraet. Sie steht weiter in der Anzeige, damit ein Auseinanderlaufen der beiden
     * Zahlen auffaellt.
     */
    private OptionalDouble verifiedWatt(OpenChargingSession open) {
        if (open.verifyStartedAt().isEmpty() || open.verifyEndedAt().isEmpty()) {
            return OptionalDouble.empty();
        }
        Instant pausedAt = open.verifyStartedAt().get();
        Instant resumedAt = open.verifyEndedAt().get();
        OptionalDouble charging =
                samples.medianConsumptionBetween(pausedAt.minus(baselineWindow), pausedAt);
        OptionalDouble paused =
                samples.medianConsumptionBetween(pausedAt.plus(settleTime), resumedAt);
        if (charging.isEmpty() || paused.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Math.max(0, charging.getAsDouble() - paused.getAsDouble()));
    }

    private Duration pausedFor(OpenChargingSession open) {
        if (open.verifyStartedAt().isEmpty() || open.verifyEndedAt().isEmpty()) {
            return Duration.ZERO;
        }
        return Duration.between(open.verifyStartedAt().get(), open.verifyEndedAt().get());
    }
}
