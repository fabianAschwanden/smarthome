package fabianaschwanden.smarthome.domain.model.appliance;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Collections;
import java.util.Map;

/**
 * Momentaufnahme einer Wellness-Anlage (Whirlpool/Schwimmbecken) mit mehreren
 * schaltbaren Funktionen. {@code functions} enthält nur die tatsächlich
 * vorhandenen Funktionen mit ihrem aktuellen Zustand.
 *
 * <p>Value Object: immutable {@code record} (defensive Kopie der Funktions-Map).
 */
public record Appliance(
        String id,
        String name,
        String room,
        boolean online,
        Instant observedAt,
        Map<ApplianceFunction, FunctionState> functions,
        Temperature temperature) {

    public Appliance {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id darf nicht leer sein");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name darf nicht leer sein");
        }
        if (room == null) {
            room = "";
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt darf nicht null sein");
        }
        if (functions == null || functions.isEmpty()) {
            throw new IllegalArgumentException("eine Anlage braucht mindestens eine Funktion");
        }
        functions = Collections.unmodifiableMap(new EnumMap<>(functions));
        // temperature darf null sein (Anlage ohne Heizung).
    }

    public boolean has(ApplianceFunction function) {
        return functions.containsKey(function);
    }
}
