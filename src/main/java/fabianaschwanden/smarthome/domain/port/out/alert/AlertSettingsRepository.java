package fabianaschwanden.smarthome.domain.port.out.alert;

import fabianaschwanden.smarthome.domain.model.alert.AlertSettings;

import java.util.Optional;

/** Getriebener Port: persistiert die (einzigen) Alert-Einstellungen. */
public interface AlertSettingsRepository {

    /** Gespeicherte Einstellungen, oder {@code empty}, falls noch nie gesetzt. */
    Optional<AlertSettings> load();

    /** Speichert (überschreibt) die Einstellungen. */
    void save(AlertSettings settings);
}
