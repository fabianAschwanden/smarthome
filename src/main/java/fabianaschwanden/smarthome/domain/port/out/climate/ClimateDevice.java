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

    /** Liest den aktuellen Zustand; {@code empty}, wenn nicht erreichbar. */
    Optional<State> readState();

    /** Schnappschuss der Geräte-Zustände (interne Transport-Struktur des Ports). */
    record State(boolean power, ClimateMode mode, int targetTemp, int currentTemp) {
    }
}
