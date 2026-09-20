package fabianaschwanden.smarthome.testsupport;

import fabianaschwanden.smarthome.domain.port.out.activation.DeviceActivationRepository;
import fabianaschwanden.smarthome.domain.model.activation.DeviceKind;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Attrappe fuer Tests: haelt die stillgelegten Geraete im Speicher. */
public final class InMemoryActivation implements DeviceActivationRepository {

    private final Map<DeviceKind, Set<String>> deactivated = new EnumMap<>(DeviceKind.class);

    @Override
    public Set<String> deactivated(DeviceKind kind) {
        return Set.copyOf(deactivated.getOrDefault(kind, Set.of()));
    }

    @Override
    public void deactivate(DeviceKind kind, String deviceId, Instant at) {
        deactivated.computeIfAbsent(kind, k -> new HashSet<>()).add(deviceId);
    }

    @Override
    public void reactivate(DeviceKind kind, String deviceId) {
        deactivated.getOrDefault(kind, new HashSet<>()).remove(deviceId);
    }
}
