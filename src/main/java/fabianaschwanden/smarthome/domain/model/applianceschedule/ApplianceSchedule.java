package fabianaschwanden.smarthome.domain.model.applianceschedule;

import java.time.Instant;
import java.util.UUID;

/**
 * Ein einmaliger Auftrag, zum Zeitpunkt {@code fireAt} die Soll-Temperatur einer
 * Wellness-Anlage auf {@code targetTemp} zu stellen.
 *
 * <p><b>Warum die Temperatur und kein Ein/Aus:</b> Die Heizung eines Gecko-Spas ist kein
 * Schalter. Sie ist dauerhaft aktiv und wird ausschliesslich über die Soll-Temperatur
 * geregelt – der Adapter weist ein Schalten ausdrücklich zurück. Wer im Überschussfenster
 * aufheizen will, hebt also die Soll-Temperatur an und senkt sie danach wieder.
 *
 * <p>Bewusst nur einmalig (Countdown) und ohne wiederkehrende Regeln: Der Anlass sind die
 * Überschussfenster aus der PV-Prognose, und die gelten für einen Tag.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record ApplianceSchedule(
        UUID id, String applianceId, int targetTemp, Instant fireAt, boolean enabled) {

    public ApplianceSchedule {
        if (id == null) {
            throw new IllegalArgumentException("id darf nicht null sein");
        }
        if (applianceId == null || applianceId.isBlank()) {
            throw new IllegalArgumentException("applianceId darf nicht leer sein");
        }
        if (fireAt == null) {
            throw new IllegalArgumentException("fireAt darf nicht null sein");
        }
    }

    public static ApplianceSchedule countdown(String applianceId, int targetTemp, Instant fireAt) {
        return new ApplianceSchedule(UUID.randomUUID(), applianceId, targetTemp, fireAt, true);
    }

    public ApplianceSchedule withEnabled(boolean newEnabled) {
        return new ApplianceSchedule(id, applianceId, targetTemp, fireAt, newEnabled);
    }

    /** Fällig, sobald der Zeitpunkt erreicht ist. */
    public boolean isDue(Instant now) {
        return !now.isBefore(fireAt);
    }
}
