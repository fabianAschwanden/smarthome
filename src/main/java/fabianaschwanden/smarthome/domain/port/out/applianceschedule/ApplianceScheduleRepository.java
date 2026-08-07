package fabianaschwanden.smarthome.domain.port.out.applianceschedule;

import fabianaschwanden.smarthome.domain.model.applianceschedule.ApplianceSchedule;

import java.util.List;
import java.util.UUID;

/** Getriebener Port: Schaltaufträge für Wellness-Anlagen. */
public interface ApplianceScheduleRepository {

    ApplianceSchedule save(ApplianceSchedule schedule);

    List<ApplianceSchedule> all();

    List<ApplianceSchedule> allEnabled();

    void delete(UUID id);
}
