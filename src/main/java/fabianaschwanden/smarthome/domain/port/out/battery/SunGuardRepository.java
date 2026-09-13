package fabianaschwanden.smarthome.domain.port.out.battery;

import fabianaschwanden.smarthome.domain.model.battery.SunGuard;

/** Getriebener Port: der Stand des Ohne-Sonne-Ausschalters (genau ein Datensatz). */
public interface SunGuardRepository {

    SunGuard load();

    void save(SunGuard guard);
}
