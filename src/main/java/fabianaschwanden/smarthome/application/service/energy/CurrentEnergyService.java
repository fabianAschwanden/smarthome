package fabianaschwanden.smarthome.application.service.energy;

import fabianaschwanden.smarthome.domain.model.energy.EnergySnapshot;
import fabianaschwanden.smarthome.domain.model.energy.PowerReading;
import fabianaschwanden.smarthome.domain.port.in.energy.CurrentEnergyQuery;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySourceGateway;
import fabianaschwanden.smarthome.domain.service.energy.EnergyComparison;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.List;

/**
 * Application-Service: orchestriert den Use Case „aktueller Energieverbrauch".
 * Liest alle angebundenen Quellen, delegiert Aggregation/Vergleich an den
 * Domain-Service. Enthält bewusst keine Geschäftsregeln.
 */
@ApplicationScoped
public class CurrentEnergyService implements CurrentEnergyQuery {

    private final List<EnergySourceGateway> gateways;
    private final Clock clock;
    private final EnergyComparison comparison = new EnergyComparison();

    @Inject
    public CurrentEnergyService(@All List<EnergySourceGateway> gateways) {
        this(gateways, Clock.systemUTC());
    }

    // Sichtbar fürs Testen (deterministische Zeit).
    CurrentEnergyService(List<EnergySourceGateway> gateways, Clock clock) {
        this.gateways = List.copyOf(gateways);
        this.clock = clock;
    }

    @Override
    public EnergySnapshot currentEnergy() {
        List<PowerReading> readings = gateways.stream()
                .map(EnergySourceGateway::read)
                .toList();
        return comparison.toSnapshot(clock.instant(), readings);
    }
}
