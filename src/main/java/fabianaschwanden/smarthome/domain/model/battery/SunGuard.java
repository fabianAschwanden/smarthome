package fabianaschwanden.smarthome.domain.model.battery;

import java.time.Instant;

/**
 * Der Ohne-Sonne-Ausschalter: schaltet die Batterieladung ab, sobald die PV-Anlage
 * nichts mehr liefert. Er schaltet nur AUS – wann geladen wird, entscheiden weiterhin
 * die Zeitsteuerung, die Lade-Automatik und der SMARTFOX.
 *
 * <p>{@code armed} ist der Merker «die Sonne war da». Nur ein scharfer Wächter schaltet
 * ab, und nach dem Abschalten ist er stumpf, bis die Sonne wiederkommt. Ohne diesen
 * Merker würgte er jeden nächtlichen Einschaltversuch binnen einer Minute wieder ab –
 * wer nachts bewusst laden will, soll das dürfen.
 *
 * <p>Value Object: immutable {@code record}, „Mutation" liefert eine neue Instanz.
 *
 * @param enabled       ob der Wächter eingeschaltet ist
 * @param armed         ob seit dem letzten Abschalten wieder Sonne gesehen wurde
 * @param lastTrippedAt wann er zuletzt tatsächlich abgeschaltet hat (null: noch nie)
 */
public record SunGuard(boolean enabled, boolean armed, Instant lastTrippedAt) {

    /** Standard: aus. Etwas, das von selbst schaltet, ist eine bewusste Entscheidung. */
    public static SunGuard disabled() {
        return new SunGuard(false, false, null);
    }

    public SunGuard withEnabled(boolean newEnabled) {
        return new SunGuard(newEnabled, armed, lastTrippedAt);
    }

    public SunGuard withArmed(boolean nowArmed) {
        return new SunGuard(enabled, nowArmed, lastTrippedAt);
    }

    /** Hat abgeschaltet: der Merker fällt, der Zeitpunkt bleibt für die Anzeige stehen. */
    public SunGuard trippedAt(Instant at) {
        if (at == null) {
            throw new IllegalArgumentException("Auslösezeitpunkt darf nicht null sein");
        }
        return new SunGuard(enabled, false, at);
    }
}
