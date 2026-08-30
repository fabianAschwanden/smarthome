package fabianaschwanden.smarthome.domain.model.charging;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalDouble;

/**
 * Ein abgeschlossener Ladevorgang der Batterie: von wann bis wann, mit welcher Leistung
 * und wie viel Energie das ergibt.
 *
 *
 * <p><b>{@code watt} ist die konfigurierte Ladeleistung, nicht gemessen.</b> Die Anlage
 * misst das Lade-Relais nicht separat, und der Umweg über den Hausverbrauch erwies sich
 * als zu ungenau: Ein Haus schwankt um ±1000 W, in derselben Grössenordnung wie die
 * gesuchte Leistung. Eine ehrliche Konstante ist mehr wert als eine Messung, die im
 * Rauschen ertrinkt.
 *
 * <p>{@code measuredWatt} ist der aus dem Verbrauch abgeleitete Wert – nur zum Vergleich,
 * damit sich die Konstante an der Wirklichkeit nachjustieren lässt.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record ChargingSession(
        Instant startedAt, Instant endedAt, double watt, double energyKwh, OptionalDouble measuredWatt) {

    public ChargingSession {
        if (measuredWatt == null) {
            throw new IllegalArgumentException("measuredWatt darf nicht null sein (leer statt null)");
        }
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
