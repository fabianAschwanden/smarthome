package fabianaschwanden.smarthome.domain.model.battery;

import java.time.Instant;

/**
 * Momentaufnahme der Batteriesteuerung: aktueller Modus, der vom System
 * gewünschte Relais-Zustand und der Zeitpunkt der letzten Änderung.
 *
 * <p>Value Object: immutable {@code record}, „Mutation" liefert eine neue Instanz.
 */
public record BatteryControl(
        ControlMode mode,
        RelayState desiredState,
        Instant changedAt) {

    public BatteryControl {
        if (mode == null) {
            throw new IllegalArgumentException("mode darf nicht null sein");
        }
        if (desiredState == null) {
            throw new IllegalArgumentException("desiredState darf nicht null sein");
        }
        if (changedAt == null) {
            throw new IllegalArgumentException("changedAt darf nicht null sein");
        }
    }

    /** Anfangszustand: Manuell, Relais aus (kein automatisches Laden ohne Freigabe). */
    public static BatteryControl initial(Instant at) {
        return new BatteryControl(ControlMode.MANUAL, RelayState.OFF, at);
    }

    public BatteryControl withMode(ControlMode newMode, Instant at) {
        return new BatteryControl(newMode, desiredState, at);
    }

    public BatteryControl withState(RelayState newState, Instant at) {
        return new BatteryControl(mode, newState, at);
    }
}
