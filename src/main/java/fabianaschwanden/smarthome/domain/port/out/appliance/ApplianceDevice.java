package fabianaschwanden.smarthome.domain.port.out.appliance;

import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.model.appliance.Temperature;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Getriebener Port: eine physische Wellness-Anlage. Adapter in
 * {@code adapter/out/appliance} implementieren diesen Port (Mock bzw. – später –
 * die reale Schnittstelle).
 */
public interface ApplianceDevice {

    String id();

    String name();

    String room();

    /** Welche Funktionen diese Anlage besitzt. */
    Set<ApplianceFunction> functions();

    /** Beheizte Anlage (Soll-/Ist-Temperatur vorhanden). */
    boolean heated();

    /** Schaltet eine Funktion. */
    void apply(ApplianceFunction function, FunctionState state);

    /** Setzt die Soll-Temperatur (nur sinnvoll bei {@link #heated()}). */
    void applyTargetTemp(int target);

    /**
     * Liest den Zustand: Funktionen und – bei beheizten Anlagen – die Temperatur.
     *
     * @return der Zustand, oder {@code empty}, wenn nicht erreichbar.
     */
    Optional<State> readState();

    /** Geräte-Zustand: Funktionszustände + optionale Temperatur ({@code null} ohne Heizung). */
    record State(Map<ApplianceFunction, FunctionState> functions, Temperature temperature) {
    }
}
