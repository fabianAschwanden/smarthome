package fabianaschwanden.smarthome.domain.port.in.coverschedule;

import fabianaschwanden.smarthome.domain.model.coverschedule.CoverSchedule;

import java.util.List;
import java.util.UUID;

/** Treiber-Port (Use Case): Storen-Zeitsteuerungs-Regeln verwalten. */
public interface ManageCoverSchedules {

    List<CoverSchedule> all();

    CoverSchedule save(CoverSchedule schedule);

    CoverSchedule setEnabled(UUID id, boolean enabled);

    void delete(UUID id);
}
