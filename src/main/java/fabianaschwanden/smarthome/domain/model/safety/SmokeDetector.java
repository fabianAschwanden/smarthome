package fabianaschwanden.smarthome.domain.model.safety;

import java.time.Instant;

/**
 * Momentaufnahme eines Rauchmelders: Alarmzustand und Batteriestand. Rauchmelder
 * sind oft batteriebetrieben und funken nur sporadisch – {@code online=false}
 * bedeutet daher „aktuell nicht erreichbar", nicht „defekt"; {@code alarm} ist dann
 * der zuletzt bekannte Zustand.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record SmokeDetector(
        String id,
        String name,
        String room,
        AlarmState alarm,
        int battery,
        boolean online,
        Instant observedAt) {

    public static final int BATTERY_UNKNOWN = -1;

    public SmokeDetector {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id darf nicht leer sein");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name darf nicht leer sein");
        }
        if (room == null) {
            room = "";
        }
        if (alarm == null) {
            throw new IllegalArgumentException("alarm darf nicht null sein");
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt darf nicht null sein");
        }
    }

    public static SmokeDetector online(String id, String name, String room, AlarmState alarm, int battery, Instant at) {
        return new SmokeDetector(id, name, room, alarm, battery, true, at);
    }

    public static SmokeDetector offline(
            String id, String name, String room, AlarmState lastAlarm, int lastBattery, Instant at) {
        return new SmokeDetector(id, name, room, lastAlarm, lastBattery, false, at);
    }
}
