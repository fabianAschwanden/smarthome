package fabianaschwanden.smarthome.application.service.cover;

import fabianaschwanden.smarthome.domain.model.cover.Cover;
import fabianaschwanden.smarthome.domain.model.cover.CoverCommand;
import fabianaschwanden.smarthome.domain.port.in.cover.ControlCovers;
import fabianaschwanden.smarthome.domain.port.in.cover.CoverNotFound;
import fabianaschwanden.smarthome.domain.port.out.cover.CoverDevice;
import fabianaschwanden.smarthome.domain.port.out.cover.CoverDeviceFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-Service: orchestriert das Steuern mehrerer Storen. Hält je Gerät die
 * zuletzt bekannte Position, gibt Befehle/Positionen über den Driven Port und liest
 * die Ist-Position zurück; nicht erreichbare Geräte werden als {@code offline}
 * gemeldet. Enthält keine Geschäftsregeln.
 */
@ApplicationScoped
public class CoverControlService implements ControlCovers {

    private final Map<String, CoverDevice> devices = new LinkedHashMap<>();
    private final Map<String, Integer> lastKnown = new ConcurrentHashMap<>();
    private final Clock clock;

    @Inject
    public CoverControlService(CoverDeviceFactory factory) {
        this(factory.devices(), Clock.systemUTC());
    }

    // Sichtbar fürs Testen.
    CoverControlService(List<CoverDevice> devices, Clock clock) {
        for (CoverDevice device : devices) {
            this.devices.put(device.id(), device);
            this.lastKnown.put(device.id(), Cover.POSITION_UNKNOWN);
        }
        this.clock = clock;
    }

    @Override
    public List<Cover> list() {
        // Alle Geraete gleichzeitig lesen, nicht nacheinander: Jedes nicht erreichbare Geraet
        // kostet einen vollen Verbindungs-Timeout (4 s). Nacheinander summierte sich das -
        // am 26.09.2026 auf 9 s fuer sechs Schalter, und die Oberflaeche zeigte gar nichts
        // mehr. Gleichzeitig ist die Liste hoechstens so langsam wie das langsamste Geraet.
        // Reihenfolge bleibt die der Konfiguration.
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            return devices.values().stream()
                    .map(device -> executor.submit(() -> observe(device)))
                    .toList().stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (java.util.concurrent.ExecutionException e) {
                            throw new IllegalStateException(e.getCause());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
        }
    }

    @Override
    public Cover command(String id, CoverCommand command) {
        CoverDevice device = require(id);
        device.apply(command);
        return observe(device);
    }

    @Override
    public Cover setPosition(String id, int position) {
        CoverDevice device = require(id);
        device.setPosition(Cover.requireValidPosition(position));
        lastKnown.put(id, position);
        return observe(device);
    }

    private CoverDevice require(String id) {
        CoverDevice device = devices.get(id);
        if (device == null) {
            throw new CoverNotFound(id);
        }
        return device;
    }

    private Cover observe(CoverDevice device) {
        OptionalInt current = device.readPosition();
        if (current.isPresent()) {
            lastKnown.put(device.id(), current.getAsInt());
            return Cover.online(device.id(), device.name(), device.room(), current.getAsInt(), clock.instant());
        }
        int known = lastKnown.getOrDefault(device.id(), Cover.POSITION_UNKNOWN);
        return Cover.offline(device.id(), device.name(), device.room(), known, clock.instant());
    }
}
