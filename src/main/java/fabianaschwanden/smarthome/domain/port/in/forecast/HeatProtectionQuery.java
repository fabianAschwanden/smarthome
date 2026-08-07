package fabianaschwanden.smarthome.domain.port.in.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.HeatProtectionWindow;

import java.util.Optional;

/**
 * Treiber-Port (Use Case): Lohnt es sich heute, die Storen gegen die Hitze zu fahren?
 *
 * <p>Leer, wenn nicht – an einem kühlen oder trüben Tag der Normalfall.
 */
public interface HeatProtectionQuery {

    Optional<HeatProtectionWindow> heatProtection();
}
