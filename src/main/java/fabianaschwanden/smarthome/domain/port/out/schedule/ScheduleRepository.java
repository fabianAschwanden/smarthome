package fabianaschwanden.smarthome.domain.port.out.schedule;

import fabianaschwanden.smarthome.domain.model.schedule.SwitchSchedule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Getriebener Port: Persistenz der Zeitsteuerungs-Regeln. Implementiert im
 * Persistence-Adapter; nimmt/liefert Domänen-Modelle, nie JPA-Entities.
 */
public interface ScheduleRepository {

    SwitchSchedule save(SwitchSchedule schedule);

    Optional<SwitchSchedule> byId(UUID id);

    List<SwitchSchedule> forSwitch(String switchId);

    /** Alle aktiven Regeln – Grundlage für den Scheduler-Tick. */
    List<SwitchSchedule> allEnabled();

    void delete(UUID id);
}
