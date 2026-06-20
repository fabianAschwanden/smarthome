package fabianaschwanden.smarthome.domain.model.cover;

import java.time.Instant;

/**
 * Momentaufnahme einer Store/Jalousie: Name, Raum, aktuelle Position und
 * Erreichbarkeit. Position in Prozent: {@code 0} = geschlossen, {@code 100} = offen,
 * {@link #POSITION_UNKNOWN} = unbekannt (Gerät meldet keine Position).
 *
 * <p>Value Object: immutable {@code record}.
 */
public record Cover(
        String id,
        String name,
        String room,
        int position,
        boolean online,
        Instant observedAt) {

    public static final int POSITION_UNKNOWN = -1;

    public Cover {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id darf nicht leer sein");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name darf nicht leer sein");
        }
        if (room == null) {
            room = "";
        }
        if (position != POSITION_UNKNOWN && (position < 0 || position > 100)) {
            throw new IllegalArgumentException("position muss 0..100 oder unbekannt sein, war " + position);
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt darf nicht null sein");
        }
    }

    public static Cover online(String id, String name, String room, int position, Instant at) {
        return new Cover(id, name, room, position, true, at);
    }

    public static Cover offline(String id, String name, String room, int lastKnown, Instant at) {
        return new Cover(id, name, room, lastKnown, false, at);
    }

    /** Validiert eine Soll-Position (0..100). */
    public static int requireValidPosition(int position) {
        if (position < 0 || position > 100) {
            throw new IllegalArgumentException("position muss 0..100 sein, war " + position);
        }
        return position;
    }
}
