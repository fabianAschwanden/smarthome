package fabianaschwanden.smarthome.domain.port.in.schedule;

import fabianaschwanden.smarthome.domain.model.schedule.SwitchSchedule;

import java.util.List;
import java.util.UUID;

/**
 * Treiber-Port (Use Case): Zeitsteuerungs-Regeln eines Schalters verwalten.
 */
public interface ManageSchedules {

    /** Alle Regeln eines Schalters. */
    List<SwitchSchedule> forSwitch(String switchId);

    /** Legt eine Regel an (oder aktualisiert sie, falls die ID existiert). */
    SwitchSchedule save(SwitchSchedule schedule);

    /** Aktiviert/deaktiviert eine Regel; liefert die geänderte Regel. */
    SwitchSchedule setEnabled(UUID id, boolean enabled);

    void delete(UUID id);
}
