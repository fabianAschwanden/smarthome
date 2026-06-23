package fabianaschwanden.smarthome.domain.port.in.batteryschedule;

import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;

import java.util.List;
import java.util.UUID;

/** Treiber-Port (Use Case): Batterie-Zeitsteuerungs-Regeln verwalten. */
public interface ManageBatterySchedules {

    List<BatterySchedule> all();

    BatterySchedule save(BatterySchedule schedule);

    BatterySchedule setEnabled(UUID id, boolean enabled);

    void delete(UUID id);
}
