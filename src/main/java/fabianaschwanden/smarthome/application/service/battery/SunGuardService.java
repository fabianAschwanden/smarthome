package fabianaschwanden.smarthome.application.service.battery;

import fabianaschwanden.smarthome.domain.model.battery.BatteryControl;
import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.battery.SunGuard;
import fabianaschwanden.smarthome.domain.port.in.battery.ControlBattery;
import fabianaschwanden.smarthome.domain.port.in.battery.ManageSunGuard;
import fabianaschwanden.smarthome.domain.port.out.battery.SunGuardRepository;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySampleRepository;
import fabianaschwanden.smarthome.domain.service.battery.SunGuardRule;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalDouble;

/**
 * Der Ohne-Sonne-Ausschalter: schaltet die Batterieladung ab, sobald die PV-Anlage
 * nichts mehr liefert.
 *
 * <p><b>Wozu.</b> Im Manuell-Modus lädt die Batterie, was das Relais hergibt – ob die
 * Sonne scheint oder nicht. Ein Ladeauftrag, der in den Abend läuft, holt den Strom
 * also aus dem Netz und macht damit genau das Gegenteil dessen, wofür er gedacht war.
 * Der Wächter ersetzt die feste Uhrzeit, zu der man das sonst von Hand beendet, durch
 * den tatsächlichen Stand der Sonne.
 *
 * <p><b>Er schaltet nur aus.</b> Wann geladen wird, entscheiden weiterhin die
 * Zeitsteuerung, die Lade-Automatik und im Automatik-Modus der SMARTFOX. Eine vierte
 * Stelle, die einschaltet, stritte mit allen dreien.
 *
 * <p><b>Er schaltet einmal pro Sonnenuntergang.</b> Nach dem Auslösen ist er stumpf,
 * bis wieder Sonne da war – sonst würgte er jeden nächtlichen Einschaltversuch binnen
 * einer Minute wieder ab.
 *
 * <p><b>Abgeschaltet heisst Manuell/Aus</b> – dasselbe, was eine Zeitsteuerungs-Regel
 * mit Aktion AUS tut. Auch aus dem Automatik-Modus heraus: Wer den Wächter einschaltet,
 * will die Ladung abends aus haben, nicht dem Gerät überlassen.
 */
@ApplicationScoped
public class SunGuardService implements ManageSunGuard {

    private static final Logger LOG = Logger.getLogger(SunGuardService.class);

    private final SunGuardRepository repository;
    private final EnergySampleRepository samples;
    private final ControlBattery battery;
    private final Clock clock;
    private final Duration window;
    private final SunGuardRule rule;

    @Inject
    public SunGuardService(
            SunGuardRepository repository,
            EnergySampleRepository samples,
            ControlBattery battery,
            @ConfigProperty(name = "battery.sun-guard.window") Duration window,
            @ConfigProperty(name = "battery.sun-guard.sun-watt") double sunWatt,
            @ConfigProperty(name = "battery.sun-guard.dark-watt") double darkWatt) {
        this(repository, samples, battery, Clock.systemUTC(), window, sunWatt, darkWatt);
    }

    // Sichtbar fürs Testen (feste Uhr, eigene Schwellen).
    SunGuardService(
            SunGuardRepository repository,
            EnergySampleRepository samples,
            ControlBattery battery,
            Clock clock,
            Duration window,
            double sunWatt,
            double darkWatt) {
        this.repository = repository;
        this.samples = samples;
        this.battery = battery;
        this.clock = clock;
        this.window = window;
        this.rule = new SunGuardRule(sunWatt, darkWatt);
    }

    @Override
    public SunGuard status() {
        return repository.load();
    }

    @Override
    public SunGuard setEnabled(boolean enabled) {
        SunGuard updated = repository.load().withEnabled(enabled);
        repository.save(updated);
        LOG.infof("Ohne-Sonne-Ausschalter %s", enabled ? "eingeschaltet" : "ausgeschaltet");
        return updated;
    }

    @Scheduled(every = "{battery.sun-guard.tick-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        try {
            evaluate();
        } catch (Exception e) {
            LOG.warnf("Ohne-Sonne-Ausschalter fehlgeschlagen: %s", e.getMessage());
        }
    }

    /** Ein Durchgang: messen, entscheiden, gegebenenfalls schalten. */
    void evaluate() {
        SunGuard guard = repository.load();
        Instant now = clock.instant();
        OptionalDouble pv = samples.medianPvBetween(now.minus(window), now);
        switch (rule.decide(guard, pv)) {
            case NOTHING -> { }
            case ARM -> {
                repository.save(guard.withArmed(true));
                LOG.infof("Ohne-Sonne-Ausschalter scharf: PV liefert wieder %.0f W",
                        pv.getAsDouble());
            }
            case TRIP -> trip(guard, now, pv.getAsDouble());
        }
    }

    /**
     * Schaltet ab – oder stellt nur den Merker zurück, wenn ohnehin schon aus ist. Ein
     * {@code lastTrippedAt} für ein Schalten, das nie stattfand, wäre eine Falschaussage
     * in der Anzeige.
     */
    private void trip(SunGuard guard, Instant now, double pvWatt) {
        BatteryControl control = battery.status();
        if (control.mode() == ControlMode.MANUAL && control.desiredState() == RelayState.OFF) {
            repository.save(guard.withArmed(false));
            LOG.debugf("Ohne-Sonne-Ausschalter: Ladung war bereits aus (PV %.0f W)", pvWatt);
            return;
        }
        battery.changeMode(ControlMode.MANUAL);
        battery.switchRelay(RelayState.OFF);
        repository.save(guard.trippedAt(now));
        LOG.infof("Ohne-Sonne-Ausschalter hat abgeschaltet: PV nur noch %.0f W", pvWatt);
    }
}
