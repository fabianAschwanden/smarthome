package fabianaschwanden.smarthome.domain.model.applianceschedule;

import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;

import java.time.Instant;
import java.util.UUID;

/**
 * Ein einmaliger Schaltauftrag für eine Wellness-Anlage: Zum Zeitpunkt {@code fireAt}
 * bekommt {@code function} den Zustand {@code state}.
 *
 * <p>Bewusst nur einmalig (Countdown) und ohne wiederkehrende Regeln: Der Anlass sind
 * die Überschussfenster aus der PV-Prognose, und die gelten für einen Tag. Eine
 * wiederkehrende Wellness-Zeitsteuerung wäre ein eigener Use Case – dieser Slice legt
 * nur den Ausführungsweg, damit die Prognose keinen zweiten Schaltpfad neben den
 * bestehenden Zeitsteuerungen aufmacht.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record ApplianceSchedule(
        UUID id,
        String applianceId,
        ApplianceFunction function,
        FunctionState state,
        Instant fireAt,
        boolean enabled) {

    public ApplianceSchedule {
        if (id == null) {
            throw new IllegalArgumentException("id darf nicht null sein");
        }
        if (applianceId == null || applianceId.isBlank()) {
            throw new IllegalArgumentException("applianceId darf nicht leer sein");
        }
        if (function == null) {
            throw new IllegalArgumentException("function darf nicht null sein");
        }
        if (state == null) {
            throw new IllegalArgumentException("state darf nicht null sein");
        }
        if (fireAt == null) {
            throw new IllegalArgumentException("fireAt darf nicht null sein");
        }
    }

    public static ApplianceSchedule countdown(
            String applianceId, ApplianceFunction function, FunctionState state, Instant fireAt) {
        return new ApplianceSchedule(UUID.randomUUID(), applianceId, function, state, fireAt, true);
    }

    public ApplianceSchedule withEnabled(boolean newEnabled) {
        return new ApplianceSchedule(id, applianceId, function, state, fireAt, newEnabled);
    }

    /** Fällig, sobald der Zeitpunkt erreicht ist. */
    public boolean isDue(Instant now) {
        return !now.isBefore(fireAt);
    }
}
