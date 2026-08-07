package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.model.forecast.AutoApplyOutcome;
import fabianaschwanden.smarthome.domain.model.forecast.AutoApplyState;
import fabianaschwanden.smarthome.domain.port.out.forecast.AutoApplyStateRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Driven Adapter — übersetzt zwischen dem Domänen-Record {@code AutoApplyState} und der
 * JPA-Entity. Fehlt die Zeile, gilt der Standard: Automatik aus.
 */
@ApplicationScoped
public class PanacheAutoApplyStateRepository
        implements AutoApplyStateRepository, PanacheRepositoryBase<AutoApplyStateEntity, String> {

    @Override
    public AutoApplyState load() {
        AutoApplyStateEntity entity = findById(AutoApplyStateEntity.SINGLETON_ID);
        if (entity == null) {
            return AutoApplyState.disabled();
        }
        return new AutoApplyState(
                entity.enabled,
                entity.lastRunDay,
                entity.lastOutcome == null ? null : AutoApplyOutcome.valueOf(entity.lastOutcome),
                entity.lastDetail);
    }

    @Override
    @Transactional
    public void save(AutoApplyState state) {
        AutoApplyStateEntity entity = findById(AutoApplyStateEntity.SINGLETON_ID);
        if (entity == null) {
            entity = new AutoApplyStateEntity();
            entity.id = AutoApplyStateEntity.SINGLETON_ID;
            persist(entity);
        }
        entity.enabled = state.enabled();
        entity.lastRunDay = state.lastRunDay();
        entity.lastOutcome = state.lastOutcome() == null ? null : state.lastOutcome().name();
        entity.lastDetail = state.lastDetail();
    }
}
