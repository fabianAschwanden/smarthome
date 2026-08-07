package fabianaschwanden.smarthome.domain.port.in.applianceschedule;

import fabianaschwanden.smarthome.domain.model.applianceschedule.ApplianceSchedule;

import java.util.List;
import java.util.UUID;

/**
 * Treiber-Port (Use Case): anstehende Schaltaufträge für Wellness-Anlagen ansehen,
 * anlegen und verwerfen.
 */
public interface ManageApplianceSchedules {

    List<ApplianceSchedule> all();

    ApplianceSchedule save(ApplianceSchedule schedule);

    void delete(UUID id);
}
