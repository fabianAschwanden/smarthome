package fabianaschwanden.smarthome.domain.port.out.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.AutoApplyState;

/** Getriebener Port: der Zustand der Lade-Automatik (genau ein Datensatz). */
public interface AutoApplyStateRepository {

    AutoApplyState load();

    void save(AutoApplyState state);
}
