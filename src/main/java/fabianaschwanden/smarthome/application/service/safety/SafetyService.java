package fabianaschwanden.smarthome.application.service.safety;

import fabianaschwanden.smarthome.domain.model.safety.AlarmState;
import fabianaschwanden.smarthome.domain.model.safety.SmokeDetector;
import fabianaschwanden.smarthome.domain.port.in.safety.ReadSafety;
import fabianaschwanden.smarthome.domain.port.out.safety.SmokeDetectorDevice;
import fabianaschwanden.smarthome.domain.port.out.safety.SmokeDetectorDeviceFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-Service: liest die Rauchmelder. Hält je Melder den zuletzt bekannten
 * Zustand und den Zeitpunkt der letzten erfolgreichen Abfrage.
 *
 * <p>Rauchmelder sind Batteriegeräte und funken nur sporadisch – einzelne
 * fehlgeschlagene Abfragen sind normal und bedeuten NICHT „offline". Daher gilt ein
 * Melder erst als {@code offline}, wenn er länger als {@link #OFFLINE_GRACE}
 * (5 Minuten) nicht mehr erreichbar war. Innerhalb der Toleranz wird der letzte
 * bekannte Zustand weiterhin als {@code online} gemeldet.
 */
@ApplicationScoped
public class SafetyService implements ReadSafety {

    private static final Duration OFFLINE_GRACE = Duration.ofMinutes(5);

    private final List<SmokeDetectorDevice> devices;
    private final Map<String, SmokeDetectorDevice.Reading> lastKnown = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();
    private final Clock clock;

    @Inject
    public SafetyService(SmokeDetectorDeviceFactory factory) {
        this(factory.devices(), Clock.systemUTC());
    }

    // Sichtbar fürs Testen.
    SafetyService(List<SmokeDetectorDevice> devices, Clock clock) {
        this.devices = List.copyOf(devices);
        this.clock = clock;
    }

    @Override
    public List<SmokeDetector> smokeDetectors() {
        return devices.stream().map(this::observe).toList();
    }

    private SmokeDetector observe(SmokeDetectorDevice device) {
        Instant now = clock.instant();
        Optional<SmokeDetectorDevice.Reading> current = device.read();
        if (current.isPresent()) {
            lastKnown.put(device.id(), current.get());
            lastSeen.put(device.id(), now);
            return SmokeDetector.online(device.id(), device.name(), device.room(),
                    current.get().alarm(), current.get().battery(), now);
        }

        SmokeDetectorDevice.Reading known = lastKnown.get(device.id());
        AlarmState alarm = known != null ? known.alarm() : AlarmState.OK;
        int battery = known != null ? known.battery() : SmokeDetector.BATTERY_UNKNOWN;

        // Innerhalb der Toleranz nach der letzten erfolgreichen Abfrage weiter als online führen.
        Instant seen = lastSeen.get(device.id());
        boolean withinGrace = seen != null && Duration.between(seen, now).compareTo(OFFLINE_GRACE) <= 0;
        if (withinGrace) {
            return SmokeDetector.online(device.id(), device.name(), device.room(), alarm, battery, now);
        }
        return SmokeDetector.offline(device.id(), device.name(), device.room(), alarm, battery, now);
    }
}
