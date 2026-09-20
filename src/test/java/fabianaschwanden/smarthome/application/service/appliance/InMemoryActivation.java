package fabianaschwanden.smarthome.application.service.appliance;

import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceActivationRepository;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Attrappe fuer Tests: haelt die stillgelegten Anlagen im Speicher. */
final class InMemoryActivation implements ApplianceActivationRepository {

    private final Set<String> deactivated = new HashSet<>();

    @Override
    public Set<String> deactivated() {
        return Set.copyOf(deactivated);
    }

    @Override
    public void deactivate(String applianceId, Instant at) {
        deactivated.add(applianceId);
    }

    @Override
    public void reactivate(String applianceId) {
        deactivated.remove(applianceId);
    }
}
