package fabianaschwanden.smarthome.application.service.appliance;

import fabianaschwanden.smarthome.domain.model.appliance.Appliance;
import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.model.appliance.Temperature;
import fabianaschwanden.smarthome.domain.port.in.appliance.ApplianceDeactivated;
import fabianaschwanden.smarthome.domain.port.in.appliance.ApplianceNotFound;
import fabianaschwanden.smarthome.domain.port.in.appliance.ControlAppliances;
import fabianaschwanden.smarthome.domain.port.in.appliance.FunctionNotSupported;
import fabianaschwanden.smarthome.domain.port.in.appliance.TemperatureNotSupported;
import fabianaschwanden.smarthome.domain.port.out.activation.DeviceActivationRepository;
import fabianaschwanden.smarthome.domain.model.activation.DeviceKind;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDevice;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDeviceFactory;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Application-Service: orchestriert das Schalten der Anlagen-Funktionen und die
 * Soll-Temperatur beheizter Anlagen. Hält je Anlage die zuletzt bekannten
 * Funktionszustände und die Temperatur; nicht erreichbare Anlagen werden als
 * {@code offline} mit dem letzten bekannten Stand gemeldet.
 *
 * <p><b>Die Soll-Temperatur wird nachgehalten, nicht nur einmal gesendet.</b> Der
 * Gecko-Befehl wirkt verzögert: Unmittelbar danach meldet die Anlage noch den alten
 * Wert. Wer den gewünschten Wert nur einmal schickt und dann die Rückmeldung anzeigt,
 * sieht den alten Wert – und der nächste Schritt rechnet wieder von dort. So kommt man
 * nie mehr als ein Grad weit. Deshalb merkt sich der Dienst den Wunsch und wiederholt
 * ihn, bis die Anlage ihn meldet oder die Versuche aufgebraucht sind.
 *
 * <p><b>Eine deaktivierte Anlage wird nicht angesprochen – gar nicht.</b> Das
 * Schwimmbecken ist über den Winter vom Strom. Ohne diese Sperre suchte der Sidecar
 * alle 30 Sekunden vergeblich 25 Sekunden lang das Spa, und aus einer stillgelegten
 * Anlage würde eine dauernd «kaputte». Deaktiviert heisst: letzter bekannter Stand
 * anzeigen, offline, Befehle abweisen, offene Temperaturwünsche verwerfen.
 */
@ApplicationScoped
public class ApplianceControlService implements ControlAppliances {

    private final Map<String, ApplianceDevice> devices = new LinkedHashMap<>();
    private final Map<String, Map<ApplianceFunction, FunctionState>> lastKnown = new ConcurrentHashMap<>();
    private final Map<String, Temperature> lastTemp = new ConcurrentHashMap<>();
    /** Gewünschte Soll-Temperaturen, die die Anlage noch nicht bestätigt hat. */
    private final Map<String, Desired> desired = new ConcurrentHashMap<>();
    private final DeviceActivationRepository activation;
    private final Clock clock;
    private final int maxAttempts;

    private static final Logger LOG = Logger.getLogger(ApplianceControlService.class);

    /** Ein offener Temperaturwunsch samt Zahl der bisherigen Versuche. */
    private record Desired(int target, int attempts) {

        Desired retried() {
            return new Desired(target, attempts + 1);
        }
    }

    @Inject
    public ApplianceControlService(
            ApplianceDeviceFactory factory,
            DeviceActivationRepository activation,
            @ConfigProperty(name = "appliance-target.max-attempts", defaultValue = "8") int maxAttempts) {
        this(factory.devices(), activation, Clock.systemUTC(), maxAttempts);
    }

    // Sichtbar fürs Testen.
    ApplianceControlService(List<ApplianceDevice> devices, DeviceActivationRepository activation, Clock clock) {
        this(devices, activation, clock, 8);
    }

    ApplianceControlService(
            List<ApplianceDevice> devices, DeviceActivationRepository activation, Clock clock, int maxAttempts) {
        this.activation = activation;
        this.maxAttempts = maxAttempts;
        for (ApplianceDevice device : devices) {
            this.devices.put(device.id(), device);
            Map<ApplianceFunction, FunctionState> initial = new EnumMap<>(ApplianceFunction.class);
            device.functions().forEach(f -> initial.put(f, FunctionState.OFF));
            this.lastKnown.put(device.id(), initial);
        }
        this.clock = clock;
    }

    @Override
    public List<Appliance> list() {
        Set<String> deactivated = activation.deactivated(DeviceKind.APPLIANCE);
        return devices.values().stream()
                .map(device -> deactivated.contains(device.id()) ? dormant(device) : observe(device))
                .toList();
    }

    @Override
    public Appliance setActive(String id, boolean active) {
        ApplianceDevice device = require(id);
        if (active) {
            activation.reactivate(DeviceKind.APPLIANCE, id);
            LOG.infof("Anlage '%s' wieder in Betrieb genommen", id);
            return observe(device);
        }
        activation.deactivate(DeviceKind.APPLIANCE, id, clock.instant());
        // Ein offener Temperaturwunsch würde sonst beim Reaktivieren - Monate später -
        // unvermittelt gestellt.
        desired.remove(id);
        LOG.infof("Anlage '%s' stillgelegt - wird nicht mehr angesprochen", id);
        return dormant(device);
    }

    @Override
    public boolean isActive(String id) {
        require(id);
        return !activation.deactivated(DeviceKind.APPLIANCE).contains(id);
    }

    @Override
    public Appliance switchFunction(String id, ApplianceFunction function, FunctionState state) {
        ApplianceDevice device = requireActive(id);
        if (!device.functions().contains(function)) {
            throw new FunctionNotSupported(id, function);
        }
        device.apply(function, state);
        lastKnown.get(id).put(function, state);
        return observe(device);
    }

    @Override
    public Appliance setTargetTemperature(String id, int target) {
        ApplianceDevice device = requireActive(id);
        if (!device.heated()) {
            throw new TemperatureNotSupported(id);
        }
        // Soll-Temperatur gegen den Bereich der Anlage prüfen, bevor das Gerät angesteuert wird.
        Temperature known = lastTemp.get(id);
        if (known != null) {
            known.requireInRange(target);  // wirft IllegalArgumentException (REST 400) bei ausserhalb
        }
        // Wunsch merken, BEVOR gesendet wird: Schlägt der Befehl fehl, wiederholt ihn
        // der Abgleich - ein verlorener Klick waere sonst nicht wiederherstellbar.
        desired.put(id, new Desired(target, 1));
        device.applyTargetTemp(target);
        return observe(device);
    }

    @Override
    public OptionalInt pendingTarget(String id) {
        Desired open = desired.get(id);
        return open == null ? OptionalInt.empty() : OptionalInt.of(open.target());
    }

    /**
     * Gleicht offene Temperaturwünsche mit dem ab, was die Anlage meldet, und schickt
     * sie nötigenfalls erneut.
     *
     * <p>Nach {@code appliance-target.max-attempts} vergeblichen Anläufen wird der Wunsch
     * verworfen. Ewig zu wiederholen hiesse, der Oberfläche einen Wert zu versprechen,
     * den die Anlage offensichtlich nicht annimmt.
     */
    @Scheduled(every = "{appliance-target.retry-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void reconcileTargets() {
        for (Map.Entry<String, Desired> entry : desired.entrySet()) {
            String id = entry.getKey();
            Desired open = entry.getValue();
            ApplianceDevice device = devices.get(id);
            if (device == null || activation.deactivated(DeviceKind.APPLIANCE).contains(id)) {
                desired.remove(id);
                continue;
            }
            Temperature temperature = observe(device).temperature();
            if (temperature != null && temperature.target() == open.target()) {
                desired.remove(id);
                LOG.debugf("'%s': Soll-Temperatur %d °C übernommen", id, open.target());
                continue;
            }
            if (open.attempts() >= maxAttempts) {
                desired.remove(id);
                LOG.warnf("'%s': Soll-Temperatur %d °C nach %d Versuchen nicht übernommen - aufgegeben",
                        id, open.target(), open.attempts());
                continue;
            }
            try {
                device.applyTargetTemp(open.target());
            } catch (RuntimeException e) {
                LOG.debugf("'%s': erneuter Versuch fehlgeschlagen: %s", id, e.getMessage());
            }
            desired.put(id, open.retried());
        }
    }

    private ApplianceDevice require(String id) {
        ApplianceDevice device = devices.get(id);
        if (device == null) {
            throw new ApplianceNotFound(id);
        }
        return device;
    }

    private ApplianceDevice requireActive(String id) {
        ApplianceDevice device = require(id);
        if (activation.deactivated(DeviceKind.APPLIANCE).contains(id)) {
            throw new ApplianceDeactivated(id);
        }
        return device;
    }

    /** Stillgelegte Anlage: letzter bekannter Stand, ohne das Gerät zu berühren. */
    private Appliance dormant(ApplianceDevice device) {
        return new Appliance(device.id(), device.name(), device.room(), false, false, clock.instant(),
                lastKnown.getOrDefault(device.id(), new EnumMap<>(ApplianceFunction.class)),
                lastTemp.get(device.id()));
    }

    private Appliance observe(ApplianceDevice device) {
        Optional<ApplianceDevice.State> current = device.readState();
        Map<ApplianceFunction, FunctionState> states;
        Temperature temperature;
        boolean online;
        if (current.isPresent()) {
            states = new EnumMap<>(current.get().functions());
            temperature = current.get().temperature();
            lastKnown.put(device.id(), new EnumMap<>(states));
            if (temperature != null) {
                lastTemp.put(device.id(), temperature);
            }
            online = true;
        } else {
            states = lastKnown.getOrDefault(device.id(), new EnumMap<>(ApplianceFunction.class));
            temperature = lastTemp.get(device.id());
            online = false;
        }
        return new Appliance(device.id(), device.name(), device.room(), online, true, clock.instant(), states, temperature);
    }
}
