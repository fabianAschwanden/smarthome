package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.model.forecast.ForecastAccuracy;
import fabianaschwanden.smarthome.domain.port.out.forecast.ForecastAccuracyRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Driven Adapter — übersetzt zwischen dem Domänen-Record {@code ForecastAccuracy} und
 * der JPA-Entity. Schlüssel ist das Datum: {@link #save(ForecastAccuracy)} legt den
 * Eintrag an oder schreibt ihn fort (etwa wenn der Ist-Wert nachgetragen wird).
 */
@ApplicationScoped
public class PanacheForecastAccuracyRepository
        implements ForecastAccuracyRepository, PanacheRepositoryBase<ForecastAccuracyEntity, LocalDate> {

    @Override
    @Transactional
    public void save(ForecastAccuracy accuracy) {
        // Erst füllen, dann persistieren - und zwar immer: Ein persist() vor dem Füllen
        // legt die Zeile mit NULL-Werten an und scheitert an den NOT-NULL-Spalten;
        // ein fehlendes persist() beim Aktualisieren lässt die Änderung verschwinden.
        ForecastAccuracyEntity entity =
                findByIdOptional(accuracy.date()).orElseGet(ForecastAccuracyEntity::new);
        entity.day = accuracy.date();
        entity.forecastKwh = accuracy.forecastKwh();
        entity.actualKwh = accuracy.actualKwh().isPresent() ? accuracy.actualKwh().getAsDouble() : null;
        persist(entity);
    }

    @Override
    public Optional<ForecastAccuracy> byDate(LocalDate date) {
        return Optional.ofNullable(findById(date)).map(PanacheForecastAccuracyRepository::toDomain);
    }

    @Override
    public List<ForecastAccuracy> latest(int limit) {
        return findAll(Sort.by("day").descending()).page(0, Math.max(1, limit)).list().stream()
                .map(PanacheForecastAccuracyRepository::toDomain)
                .toList();
    }

    private static ForecastAccuracy toDomain(ForecastAccuracyEntity entity) {
        return new ForecastAccuracy(
                entity.day,
                entity.forecastKwh,
                entity.actualKwh == null ? OptionalDouble.empty() : OptionalDouble.of(entity.actualKwh));
    }
}
