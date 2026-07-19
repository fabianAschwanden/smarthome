package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySampleRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

/** Driven Adapter — übersetzt zwischen Domänen-Record und JPA-Entity (Zeitreihe). */
@ApplicationScoped
public class PanacheEnergySampleRepository
        implements EnergySampleRepository, PanacheRepository<EnergySampleEntity> {

    @Override
    @Transactional
    public void save(EnergySample sample) {
        EnergySampleEntity e = new EnergySampleEntity();
        e.ts = sample.timestamp();
        e.pvWatt = sample.pvWatt();
        e.consumptionWatt = sample.consumptionWatt();
        persist(e);
    }

    @Override
    public List<EnergySample> between(Instant fromInclusive, Instant toExclusive) {
        return find("ts >= ?1 and ts < ?2 order by ts", fromInclusive, toExclusive)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public long deleteOlderThan(Instant cutoff) {
        return delete("ts < ?1", cutoff);
    }

    // Bewusst nicht 'count' genannt: eine explizite count()-Deklaration würde Panaches
    // Build-Time-Enhancement dieser Methode verhindern (implementationInjectionMissing).
    @Override
    public long total() {
        return count();
    }

    private EnergySample toDomain(EnergySampleEntity e) {
        return new EnergySample(e.ts, e.pvWatt, e.consumptionWatt);
    }
}
