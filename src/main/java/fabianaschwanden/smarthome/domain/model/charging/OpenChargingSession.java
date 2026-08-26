package fabianaschwanden.smarthome.domain.model.charging;

import java.time.Instant;
import java.util.Optional;

/**
 * Ein laufender Ladevorgang samt Stand der Gegenmessung.
 *
 * <p>Die Gegenmessung schaltet mitten im Laden kurz ab und wieder ein: Der Verbrauch
 * <em>fällt</em> dabei um die Ladeleistung. Das ist eine zweite, unabhängige Messung –
 * und sie entsteht im eingeschwungenen Zustand, während die erste unmittelbar nach dem
 * Einschalten fällt, wo das Ladegerät noch anläuft.
 *
 * <p>Der Stand wird mitgeschrieben, damit ein Neustart die Anlage nicht in der Pause
 * stehen lässt: Wer {@code verifyStartedAt} ohne {@code verifyEndedAt} vorfindet, muss
 * wieder einschalten.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record OpenChargingSession(
        Instant startedAt, Optional<Instant> verifyStartedAt, Optional<Instant> verifyEndedAt) {

    public OpenChargingSession {
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt darf nicht null sein");
        }
        verifyStartedAt = verifyStartedAt == null ? Optional.empty() : verifyStartedAt;
        verifyEndedAt = verifyEndedAt == null ? Optional.empty() : verifyEndedAt;
    }

    public static OpenChargingSession startedAt(Instant startedAt) {
        return new OpenChargingSession(startedAt, Optional.empty(), Optional.empty());
    }

    public OpenChargingSession verifyStarted(Instant at) {
        return new OpenChargingSession(startedAt, Optional.of(at), verifyEndedAt);
    }

    public OpenChargingSession verifyEnded(Instant at) {
        return new OpenChargingSession(startedAt, verifyStartedAt, Optional.of(at));
    }

    /** Läuft die Pause der Gegenmessung gerade? Dann ist das Relais AUS, obwohl geladen wird. */
    public boolean isPaused() {
        return verifyStartedAt.isPresent() && verifyEndedAt.isEmpty();
    }

    public boolean verificationDone() {
        return verifyEndedAt.isPresent();
    }
}
