package fabianaschwanden.smarthome.domain.port.out.appliance;

import java.time.Instant;
import java.util.Set;

/**
 * Getriebener Port: welche Anlagen bewusst stillgelegt sind. Gespeichert wird nur die
 * Ausnahme (deaktiviert) – wer nicht drinsteht, ist aktiv. So braucht eine neu
 * konfigurierte Anlage keinen Datensatz, um zu funktionieren.
 */
public interface ApplianceActivationRepository {

    /** IDs aller deaktivierten Anlagen. */
    Set<String> deactivated();

    void deactivate(String applianceId, Instant at);

    void reactivate(String applianceId);
}
