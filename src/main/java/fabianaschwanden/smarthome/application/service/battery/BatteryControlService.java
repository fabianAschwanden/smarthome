package fabianaschwanden.smarthome.application.service.battery;

import fabianaschwanden.smarthome.domain.model.battery.BatteryControl;
import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.port.in.battery.ControlBattery;
import fabianaschwanden.smarthome.domain.port.in.battery.ManualSwitchNotAllowed;
import fabianaschwanden.smarthome.domain.port.out.battery.RelaySwitch;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Clock;

/**
 * Application-Service: orchestriert die Batteriesteuerung über das dreiwertige
 * SMARTFOX-Relais 1 (Aus / Manuell / Automatik). Der Steuerstand bildet das als
 * ({@link ControlMode}, {@link RelayState}) ab: Aus=(MANUAL,OFF), Manuell=(MANUAL,ON),
 * Automatik=(AUTO, Ist-Ausgang).
 *
 * <p>Die Überschuss-Automatik macht die SMARTFOX selbst – die App setzt im AUTO-Modus
 * nur den Gerätemodus und spiegelt dessen Ist-Zustand zurück (kein eigener Algorithmus).
 * Der {@link #syncFromDevice()}-Tick hält die Anzeige mit dem echten Relais im Gleich-
 * lauf (externe Umschaltung, Automatik-Schaltvorgänge, nicht gegriffener Befehl).
 */
@ApplicationScoped
public class BatteryControlService implements ControlBattery {

    private static final Logger LOG = Logger.getLogger(BatteryControlService.class);

    private final RelaySwitch relay;
    private final Clock clock;

    private BatteryControl control;

    @Inject
    public BatteryControlService(RelaySwitch relay) {
        this(relay, Clock.systemUTC());
    }

    // Sichtbar fürs Testen (Zeit injizierbar).
    BatteryControlService(RelaySwitch relay, Clock clock) {
        this.relay = relay;
        this.clock = clock;
        this.control = BatteryControl.initial(clock.instant());
    }

    /**
     * Start-Initialisierung: die App verhält sich NEUTRAL – kein Schaltbefehl. Der
     * Ist-Zustand wird vom Gerät gelesen und übernommen; ist er nicht lesbar, bleibt es
     * beim Anfangszustand (Aus).
     */
    @PostConstruct
    synchronized void initFromDevice() {
        relay.read().ifPresent(r -> control = new BatteryControl(r.mode(), r.state(), clock.instant()));
    }

    @Override
    public synchronized BatteryControl status() {
        return control;
    }

    /**
     * Modus wechseln. Aus/Manuell behalten den zuletzt gewünschten Ein/Aus-Zustand;
     * Automatik übergibt die Kontrolle an die SMARTFOX (Zustand geräteseitig).
     */
    @Override
    public synchronized BatteryControl changeMode(ControlMode mode) {
        return apply(mode, control.desiredState());
    }

    @Override
    public synchronized BatteryControl switchRelay(RelayState state) {
        if (control.mode() != ControlMode.MANUAL) {
            throw new ManualSwitchNotAllowed();
        }
        return apply(ControlMode.MANUAL, state);
    }

    /** Stellt Modus/Zustand am Gerät – nur bei echtem Wechsel (idempotent). */
    private BatteryControl apply(ControlMode mode, RelayState state) {
        if (mode != control.mode() || state != control.desiredState()) {
            relay.apply(mode, state);
            control = new BatteryControl(mode, state, clock.instant());
            LOG.infof("Batterie gestellt -> Modus %s / %s", mode, state);
        }
        return control;
    }

    /**
     * Gleicht die Anzeige periodisch mit dem ECHTEN Relais ab (in ALLEN Modi, da die App
     * keine eigene Automatik mehr fährt). Fängt externe Umschaltung, Automatik-Schalt-
     * vorgänge und nicht gegriffene Befehle ab. Lesefehler halten den letzten Stand.
     */
    @Scheduled(every = "{battery.sync-interval}")
    synchronized void syncFromDevice() {
        relay.read().ifPresent(r -> {
            if (r.mode() != control.mode() || r.state() != control.desiredState()) {
                control = new BatteryControl(r.mode(), r.state(), clock.instant());
                LOG.infof("Relais-Ist weicht ab -> Anzeige nachgeführt: Modus %s / %s", r.mode(), r.state());
            }
        });
    }
}
