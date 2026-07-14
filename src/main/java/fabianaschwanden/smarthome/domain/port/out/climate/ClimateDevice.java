package fabianaschwanden.smarthome.domain.port.out.climate;

import fabianaschwanden.smarthome.domain.model.climate.ClimateMode;

import java.util.Optional;

/**
 * Getriebener Port: eine physische Klimaanlage. Adapter in
 * {@code adapter/out/climate} implementieren diesen Port (Mock bzw. – später – die
 * reale Schnittstelle).
 */
public interface ClimateDevice {

    String id();

    String name();

    String room();

    void applyPower(boolean on);

    void applyMode(ClimateMode mode);

    void applyTargetTemp(int temperature);

    /** Schaltet den Boost-/Turbo-Modus (maximale Leistung) ein/aus. */
    void applyBoost(boolean on);

    /** Liest den aktuellen Zustand; {@code empty}, wenn nicht erreichbar. */
    Optional<State> readState();

    /** Schnappschuss der Geräte-Zustände (interne Transport-Struktur des Ports). */
    record State(boolean power, boolean boost, ClimateMode mode, int targetTemp, int currentTemp,
                 int outdoorTemp) {
    }
}
