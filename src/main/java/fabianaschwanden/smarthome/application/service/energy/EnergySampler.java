package fabianaschwanden.smarthome.application.service.energy;

import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
import fabianaschwanden.smarthome.domain.model.energy.PowerReading;
import fabianaschwanden.smarthome.domain.model.energy.PowerSource;
import fabianaschwanden.smarthome.domain.port.in.energy.CurrentEnergyQuery;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySampleRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

/**
 * Zeichnet periodisch die Momentanleistung (PV-Produktion, Hausverbrauch) der
 * bevorzugten Energiequelle auf, damit ein Tages-/Wochen-/Monatsverlauf entsteht.
 * Bevorzugt wird die erste OK-Messung von {@link PowerSource#FRONIUS} (liefert die
 * verlässlichsten Werte inkl. Tageszähler), sonst die erste OK-Messung überhaupt.
 * Eine tote Quelle wird übersprungen (kein Nullwert), damit keine Scheinlücken als
 * „0 kWh" gespeichert werden.
 */
@ApplicationScoped
public class EnergySampler {

    private static final Logger LOG = Logger.getLogger(EnergySampler.class);

    private final CurrentEnergyQuery currentEnergy;
    private final EnergySampleRepository repository;
    private final Clock clock;
    private final int retentionDays;

    @Inject
    public EnergySampler(
            CurrentEnergyQuery currentEnergy,
            EnergySampleRepository repository,
            @ConfigProperty(name = "energy.retention-days", defaultValue = "45") int retentionDays) {
        this(currentEnergy, repository, Clock.systemUTC(), retentionDays);
    }

    // Sichtbar fürs Testen.
    EnergySampler(CurrentEnergyQuery currentEnergy, EnergySampleRepository repository,
                  Clock clock, int retentionDays) {
        this.currentEnergy = currentEnergy;
        this.repository = repository;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Scheduled(every = "{energy.sample.tick-interval}")
    void sample() {
        primaryReading().ifPresent(r -> {
            repository.save(new EnergySample(clock.instant(), r.pvWatt(), r.consumptionWatt()));
            LOG.debugf("Energie-Sample: PV %.0f W, Verbrauch %.0f W (%s)",
                    r.pvWatt(), r.consumptionWatt(), r.source());
        });
    }

    @Scheduled(every = "{energy.retention.cleanup-interval}")
    void cleanup() {
        Instant cutoff = clock.instant().minus(java.time.Duration.ofDays(retentionDays));
        long removed = repository.deleteOlderThan(cutoff);
        if (removed > 0) {
            LOG.infof("Energie-Historie aufgeräumt: %d alte Messpunkte gelöscht (älter als %d Tage)",
                    removed, retentionDays);
        }
    }

    /** Erste OK-Messung – Fronius bevorzugt, sonst die erste verfügbare. */
    private Optional<PowerReading> primaryReading() {
        return currentEnergy.currentEnergy().readings().stream()
                .filter(PowerReading::isOk)
                .min(Comparator.comparingInt(r -> r.source() == PowerSource.FRONIUS ? 0 : 1));
    }
}
