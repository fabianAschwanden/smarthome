package fabianaschwanden.smarthome.domain.port.in.battery;

import fabianaschwanden.smarthome.domain.model.battery.SunGuard;

/**
 * Treiber-Port (Use Case): den Ohne-Sonne-Ausschalter abfragen und ein-/ausschalten.
 * Das Abschalten selbst treibt der Scheduler, nicht der Aufrufer.
 */
public interface ManageSunGuard {

    /** Aktueller Stand: ein/aus, scharf, wann zuletzt abgeschaltet. */
    SunGuard status();

    /** Wächter ein- oder ausschalten; liefert den neuen Stand. */
    SunGuard setEnabled(boolean enabled);
}
