package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.model.sensor.SensorSample;
import fabianaschwanden.smarthome.domain.port.out.sensor.SensorSampleRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Driven Adapter — übersetzt zwischen dem Domänen-Record {@code SensorSample} und der
 * JPA-Entity.
 */
@ApplicationScoped
public class PanacheSensorSampleRepository
        implements SensorSampleRepository, PanacheRepository<SensorSampleEntity> {

    /**
     * Verdichtung als eine Anweisung in der Datenbank statt als Schleife in Java: Nach
     * einem Jahr stehen je Sensor über 50 000 Punkte an, die zeilenweise zu laden und
     * einzeln zu löschen wäre um Grössenordnungen teurer. Behalten wird je Stunde der
     * <em>erste</em> Punkt – welcher es ist, spielt fachlich keine Rolle, aber die Wahl
     * muss deterministisch sein, damit ein zweiter Lauf nichts mehr findet.
     */
    private static final String COMPACT_SQL = """
            DELETE FROM sensor_sample WHERE id IN (
              SELECT id FROM (
                SELECT id, row_number() OVER (
                  PARTITION BY sensor_id, date_trunc('hour', ts) ORDER BY ts
                ) AS rn
                FROM sensor_sample WHERE ts < ?1
              ) ranked WHERE ranked.rn > 1
            )
            """;

    @Override
    @Transactional
    public void save(SensorSample sample) {
        SensorSampleEntity entity = new SensorSampleEntity();
        entity.sensorId = sample.sensorId();
        entity.ts = sample.timestamp();
        entity.temperature = sample.temperature();
        entity.humidity = sample.humidity();
        persist(entity);
    }

    @Override
    public List<SensorSample> between(Instant fromInclusive, Instant toExclusive) {
        return find("ts >= ?1 and ts < ?2", Sort.by("ts"), fromInclusive, toExclusive).list().stream()
                .map(PanacheSensorSampleRepository::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public long compactOlderThan(Instant cutoff) {
        return getEntityManager().createNativeQuery(COMPACT_SQL).setParameter(1, cutoff).executeUpdate();
    }

    @Override
    public long total() {
        return count();
    }

    private static SensorSample toDomain(SensorSampleEntity entity) {
        return new SensorSample(entity.sensorId, entity.ts, entity.temperature, entity.humidity);
    }
}
