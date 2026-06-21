package fabianaschwanden.smarthome.domain.model.tuya;

import java.time.Instant;

/**
 * Momentaufnahme eines Tuya-Schalters: stabile ID, Name, Raum, Zustand,
 * Erreichbarkeit und Zeitpunkt der letzten Beobachtung. Ist das Gerät nicht
 * erreichbar, ist {@code online=false} und {@code state} der zuletzt bekannte Wert.
 *
 * <p>{@code critical}: Ausschalten erfordert eine ausdrückliche Bestätigung (z. B.
 * der Homecinema-Schalter versorgt auch das WLAN).
 *
 * <p>Value Object: immutable {@code record}.
 */
public record TuyaSwitch(
        String id,
        String name,
        String room,
        SwitchState state,
        boolean online,
        boolean critical,
        String hint,
        Instant observedAt) {

    public TuyaSwitch {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id darf nicht leer sein");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name darf nicht leer sein");
        }
        if (room == null) {
            room = "";
        }
        if (hint == null) {
            hint = "";
        }
        if (state == null) {
            throw new IllegalArgumentException("state darf nicht null sein");
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt darf nicht null sein");
        }
    }

    public static TuyaSwitch online(
            String id, String name, String room, SwitchState state, boolean critical, String hint, Instant at) {
        return new TuyaSwitch(id, name, room, state, true, critical, hint, at);
    }

    /** Gerät nicht erreichbar; {@code lastKnown} ist der zuletzt bekannte Zustand. */
    public static TuyaSwitch offline(
            String id, String name, String room, SwitchState lastKnown, boolean critical, String hint, Instant at) {
        return new TuyaSwitch(id, name, room, lastKnown, false, critical, hint, at);
    }
}
