package fabianaschwanden.smarthome.domain.port.out.coverschedule;

import fabianaschwanden.smarthome.domain.model.coverschedule.CoverSchedule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Getriebener Port: Persistenz der Storen-Zeitsteuerungs-Regeln. Implementiert im
 * Persistence-Adapter; nimmt/liefert Domänen-Modelle, nie JPA-Entities.
 */
public interface CoverScheduleRepository {

    CoverSchedule save(CoverSchedule schedule);

    Optional<CoverSchedule> byId(UUID id);

    List<CoverSchedule> all();

    /** Alle aktiven Regeln – Grundlage für den Scheduler-Tick. */
    List<CoverSchedule> allEnabled();

    void delete(UUID id);
}
