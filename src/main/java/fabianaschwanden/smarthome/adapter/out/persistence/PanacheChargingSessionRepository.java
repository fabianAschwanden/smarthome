package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.model.charging.ChargingSession;
import fabianaschwanden.smarthome.domain.model.charging.OpenChargingSession;
import fabianaschwanden.smarthome.domain.port.out.charging.ChargingSessionRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Driven Adapter — übersetzt zwischen dem Domänen-Record {@code ChargingSession} und der
 * JPA-Entity. Sorgt dafür, dass höchstens ein Vorgang offen ist.
 */
@ApplicationScoped
public class PanacheChargingSessionRepository
        implements ChargingSessionRepository, PanacheRepository<ChargingSessionEntity> {

    @Override
    @Transactional
    public void open(Instant startedAt) {
        if (openEntity().isPresent()) {
            // Schon offen: Der Beginn des laufenden Vorgangs zaehlt, nicht der spaetere
            // Blick darauf. Ein zweites Oeffnen wuerde die Dauer verkuerzen.
            return;
        }
        ChargingSessionEntity entity = new ChargingSessionEntity();
        entity.startedAt = startedAt;
        persist(entity);
    }

    @Override
    public Optional<OpenChargingSession> open() {
        return openEntity().map(entity -> new OpenChargingSession(
                entity.startedAt,
                Optional.ofNullable(entity.verifyStartedAt),
                Optional.ofNullable(entity.verifyEndedAt)));
    }

    @Override
    @Transactional
    public void updateOpen(OpenChargingSession session) {
        openEntity().ifPresent(entity -> {
            entity.verifyStartedAt = session.verifyStartedAt().orElse(null);
            entity.verifyEndedAt = session.verifyEndedAt().orElse(null);
            persist(entity);
        });
    }

    @Override
    @Transactional
    public void close(ChargingSession session) {
        openEntity().ifPresent(entity -> {
            entity.endedAt = session.endedAt();
            entity.watt = session.watt();
            entity.energyKwh = session.energyKwh();
            entity.verifyWatt = session.verifiedWatt().isPresent()
                    ? session.verifiedWatt().getAsDouble()
                    : null;
            persist(entity);
        });
    }

    @Override
    @Transactional
    public void discardOpen() {
        openEntity().ifPresent(this::delete);
    }

    @Override
    public List<ChargingSession> latest(int limit) {
        return find("endedAt is not null", Sort.by("startedAt").descending())
                .page(0, Math.max(1, limit))
                .list().stream()
                .map(PanacheChargingSessionRepository::toDomain)
                .toList();
    }

    private Optional<ChargingSessionEntity> openEntity() {
        return find("endedAt is null", Sort.by("startedAt").descending()).firstResultOptional();
    }

    private static ChargingSession toDomain(ChargingSessionEntity entity) {
        return new ChargingSession(
                entity.startedAt,
                entity.endedAt,
                entity.watt,
                entity.energyKwh,
                entity.verifyWatt == null
                        ? java.util.OptionalDouble.empty()
                        : java.util.OptionalDouble.of(entity.verifyWatt));
    }
}
