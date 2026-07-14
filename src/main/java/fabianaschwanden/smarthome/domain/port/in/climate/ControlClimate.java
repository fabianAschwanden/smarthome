package fabianaschwanden.smarthome.domain.port.in.climate;

import fabianaschwanden.smarthome.domain.model.climate.Climate;
import fabianaschwanden.smarthome.domain.model.climate.ClimateMode;

import java.util.List;

/**
 * Treiber-Port (Use Case): Klimaanlagen verwalten – auflisten, ein/aus, Boost, Modus
 * und Soll-Temperatur setzen.
 */
public interface ControlClimate {

    List<Climate> list();

    Climate setPower(String id, boolean on);

    Climate setMode(String id, ClimateMode mode);

    /** Soll-Temperatur in °C (gültiger Bereich siehe {@code Climate}). */
    Climate setTargetTemp(String id, int temperature);

    /** Boost-/Turbo-Modus (maximale Leistung) ein/aus. */
    Climate setBoost(String id, boolean on);
}
