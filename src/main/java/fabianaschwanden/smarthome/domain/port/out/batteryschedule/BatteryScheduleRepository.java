package fabianaschwanden.smarthome.domain.port.out.batteryschedule;

import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Getriebener Port: Persistenz der Batterie-Zeitsteuerungs-Regeln. Implementiert im
 * Persistence-Adapter; nimmt/liefert Domänen-Modelle, nie JPA-Entities.
 */
public interface BatteryScheduleRepository {

    BatterySchedule save(BatterySchedule schedule);

    Optional<BatterySchedule> byId(UUID id);

    List<BatterySchedule> all();

    /** Alle aktiven Regeln – Grundlage für den Scheduler-Tick. */
    List<BatterySchedule> allEnabled();

    void delete(UUID id);
}
