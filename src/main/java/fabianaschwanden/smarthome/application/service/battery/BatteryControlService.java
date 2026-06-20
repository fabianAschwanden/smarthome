package fabianaschwanden.smarthome.application.service.battery;

import fabianaschwanden.smarthome.domain.model.battery.BatteryControl;
import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.port.in.battery.ControlBattery;
import fabianaschwanden.smarthome.domain.port.in.battery.ManualSwitchNotAllowed;
import fabianaschwanden.smarthome.domain.port.out.battery.RelaySwitch;
import fabianaschwanden.smarthome.domain.service.battery.SurplusChargePolicy;
import fabianaschwanden.smarthome.domain.model.energy.PowerReading;
import fabianaschwanden.smarthome.domain.model.energy.PowerSource;
import fabianaschwanden.smarthome.domain.port.in.energy.CurrentEnergyQuery;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;

/**
 * Application-Service: orchestriert die Batteriesteuerung. Hält den Steuerstand
 * im Speicher (kein DB-Zwang, vgl. Energie-SPEC §7), treibt den Auto-Modus per
 * Scheduler und schaltet das Relais ausschliesslich bei echten Zustandswechseln
 * (idempotent, SPEC §7). Enthält keine Geschäftsregeln – die Überschuss-Logik
 * lebt im Domain-Service {@link SurplusChargePolicy}.
 */
@ApplicationScoped
public class BatteryControlService implements ControlBattery {

    private static final Logger LOG = Logger.getLogger(BatteryControlService.class);

    private final CurrentEnergyQuery energy;
    private final RelaySwitch relay;
    private final SurplusChargePolicy policy;
    private final PowerSource referenceSource;
    private final Clock clock;

    private BatteryControl control;

    @Inject
    public BatteryControlService(
            CurrentEnergyQuery energy,
            RelaySwitch relay,
            @ConfigProperty(name = "battery.auto.charge-on-watt", defaultValue = "1500") double chargeOnWatt,
            @ConfigProperty(name = "battery.auto.charge-off-watt", defaultValue = "300") double chargeOffWatt,
            @ConfigProperty(name = "energy.reference-source", defaultValue = "SMARTFOX") PowerSource referenceSource) {
        this(energy, relay, new SurplusChargePolicy(chargeOnWatt, chargeOffWatt),
                referenceSource, Clock.systemUTC());
    }

    // Sichtbar fürs Testen (Policy/Referenz/Zeit injizierbar).
    BatteryControlService(
            CurrentEnergyQuery energy,
            RelaySwitch relay,
            SurplusChargePolicy policy,
            PowerSource referenceSource,
            Clock clock) {
        this.energy = energy;
        this.relay = relay;
        this.policy = policy;
        this.referenceSource = referenceSource;
        this.clock = clock;
        this.control = BatteryControl.initial(clock.instant());
    }

    @PostConstruct
    void applyInitialState() {
        relay.apply(control.desiredState());
    }

    @Override
    public synchronized BatteryControl status() {
        return control;
    }

    @Override
    public synchronized BatteryControl changeMode(ControlMode mode) {
        control = control.withMode(mode, clock.instant());
        return control;
    }

    @Override
    public synchronized BatteryControl switchRelay(RelayState state) {
        if (control.mode() != ControlMode.MANUAL) {
            throw new ManualSwitchNotAllowed();
        }
        return applyState(state);
    }

    /** Auto-Modus: periodisch den Überschuss auswerten und das Relais nachführen. */
    @Scheduled(every = "{battery.auto.tick-interval}")
    synchronized void autoTick() {
        if (control.mode() != ControlMode.AUTO) {
            return;
        }
        double surplusWatt = -referenceGridWatt();
        RelayState target = policy.decide(surplusWatt, control.desiredState());
        applyState(target);
    }

    private BatteryControl applyState(RelayState state) {
        if (state != control.desiredState()) {
            relay.apply(state);
            control = control.withState(state, clock.instant());
            LOG.infof("Relais -> %s (Modus %s)", state, control.mode());
        }
        return control;
    }

    /** Netzleistung der Referenzquelle; 0 W, wenn keine gültige Messung vorliegt. */
    private double referenceGridWatt() {
        return energy.currentEnergy().readings().stream()
                .filter(reading -> reading.source() == referenceSource && reading.isOk())
                .mapToDouble(PowerReading::gridWatt)
                .findFirst()
                .orElse(0.0);
    }
}
