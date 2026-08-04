package fabianaschwanden.smarthome.domain.port.out.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.PlantProfile;

import java.util.Optional;

/**
 * Speichert genau ein gelerntes Anlagenprofil – neu lernen überschreibt das alte.
 *
 * <p>Es gibt bewusst keine Historie: Das Profil beschreibt den aktuellen Zustand der
 * Anlage (inklusive Verschmutzung und Alterung), ältere Stände hätten keinen fachlichen
 * Nutzen. {@code empty} heisst „noch nie gelernt" und führt zum Cold-Start-Fallback.
 */
public interface PlantProfileRepository {

    Optional<PlantProfile> load();

    void save(PlantProfile profile);
}
