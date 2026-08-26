package fabianaschwanden.smarthome.domain.model.charging;

import java.time.Duration;
import java.time.Instant;

/**
 * Ein abgeschlossener Ladevorgang der Batterie: von wann bis wann, mit welcher Leistung
 * und wie viel Energie das ergibt.
 *
 * <p><b>{@code watt} ist gemessen, {@code energyKwh} gerechnet.</b> Die Leistung stammt
 * aus dem Verbrauchssprung beim Einschalten – die Anlage misst das Lade-Relais nicht
 * separat, deshalb ist dieser Sprung die einzige Spur, die das Ladegerät hinterlässt.
 * Die Energie ist daraus mal Dauer, also eine <em>Schätzung</em> unter der Annahme
 * konstanter Ladeleistung. Wer eine belastbare Zahl braucht, braucht einen eigenen Zähler.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record ChargingSession(Instant startedAt, Instant endedAt, double watt, double energyKwh) {

    public ChargingSession {
        if (startedAt == null || endedAt == null) {
            throw new IllegalArgumentException("startedAt und endedAt dürfen nicht null sein");
        }
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("endedAt darf nicht vor startedAt liegen");
        }
        if (watt < 0) {
            throw new IllegalArgumentException("watt darf nicht negativ sein: " + watt);
        }
        if (energyKwh < 0) {
            throw new IllegalArgumentException("energyKwh darf nicht negativ sein: " + energyKwh);
        }
    }

    public Duration duration() {
        return Duration.between(startedAt, endedAt);
    }
}
