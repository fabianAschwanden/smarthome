package fabianaschwanden.smarthome.domain.model.schedule;

import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Zeitsteuerungs-Regel für einen Schalter. Ein Aggregat deckt alle vier
 * {@link ScheduleType}-Arten ab; je Typ sind unterschiedliche Felder relevant –
 * die Invarianten dazu erzwingt der Compact-Constructor (fail fast).
 *
 * <p>Immutable {@code record}: „Mutation" (z. B. aktivieren) liefert eine neue Instanz.
 *
 * @param id           Identität
 * @param switchId     Ziel-Schalter (technische ID, vgl. Tuya-Geräte)
 * @param type         Art der Steuerung
 * @param action       gewünschter Zustand (für RANDOM/INCHING ist {@code ON} der Auslöser)
 * @param enabled      ob die Regel aktiv ist
 * @param time         SCHEDULE: Uhrzeit (lokale Zeit)
 * @param weekdays     SCHEDULE: Wochentage (leer = täglich)
 * @param fireAt       COUNTDOWN: einmaliger Auslösezeitpunkt
 * @param windowStart  RANDOM: Beginn des Zeitfensters
 * @param windowEnd    RANDOM: Ende des Zeitfensters
 * @param pulseSeconds INCHING: Dauer bis zum automatischen Ausschalten
 */
public record SwitchSchedule(
        UUID id,
        String switchId,
        ScheduleType type,
        SwitchState action,
        boolean enabled,
        LocalTime time,
        Set<DayOfWeek> weekdays,
        Instant fireAt,
        LocalTime windowStart,
        LocalTime windowEnd,
        Integer pulseSeconds) {

    public SwitchSchedule {
        if (id == null) {
            throw new IllegalArgumentException("id darf nicht null sein");
        }
        if (switchId == null || switchId.isBlank()) {
            throw new IllegalArgumentException("switchId darf nicht leer sein");
        }
        if (type == null) {
            throw new IllegalArgumentException("type darf nicht null sein");
        }
        if (action == null) {
            throw new IllegalArgumentException("action darf nicht null sein");
        }
        weekdays = weekdays == null ? Set.of() : Set.copyOf(weekdays);
        switch (type) {
            case SCHEDULE -> {
                if (time == null) {
                    throw new IllegalArgumentException("SCHEDULE braucht eine Uhrzeit");
                }
            }
            case COUNTDOWN -> {
                if (fireAt == null) {
                    throw new IllegalArgumentException("COUNTDOWN braucht einen Auslösezeitpunkt");
                }
            }
            case RANDOM -> {
                if (windowStart == null || windowEnd == null) {
                    throw new IllegalArgumentException("RANDOM braucht ein Zeitfenster");
                }
            }
            case INCHING -> {
                if (pulseSeconds == null || pulseSeconds <= 0) {
                    throw new IllegalArgumentException("INCHING braucht eine positive Impulsdauer");
                }
            }
        }
    }

    public SwitchSchedule withEnabled(boolean newEnabled) {
        return new SwitchSchedule(id, switchId, type, action, newEnabled,
                time, weekdays, fireAt, windowStart, windowEnd, pulseSeconds);
    }

    /** SCHEDULE: gilt die Regel an diesem Wochentag? (leere Menge = täglich) */
    public boolean appliesOn(DayOfWeek day) {
        return weekdays.isEmpty() || weekdays.contains(day);
    }

    public Set<DayOfWeek> weekdays() {
        return Collections.unmodifiableSet(weekdays);
    }

    /** Fabrik: wiederkehrender Zeitplan. */
    public static SwitchSchedule schedule(
            UUID id, String switchId, SwitchState action, LocalTime time, Set<DayOfWeek> weekdays) {
        return new SwitchSchedule(id, switchId, ScheduleType.SCHEDULE, action, true,
                time, weekdays, null, null, null, null);
    }

    /** Fabrik: einmaliger Countdown. */
    public static SwitchSchedule countdown(UUID id, String switchId, SwitchState action, Instant fireAt) {
        return new SwitchSchedule(id, switchId, ScheduleType.COUNTDOWN, action, true,
                null, Set.of(), fireAt, null, null, null);
    }

    /** Fabrik: Zufallsschalten im Zeitfenster. */
    public static SwitchSchedule random(
            UUID id, String switchId, SwitchState action, LocalTime start, LocalTime end) {
        return new SwitchSchedule(id, switchId, ScheduleType.RANDOM, action, true,
                null, Set.of(), null, start, end, null);
    }

    /** Fabrik: Impuls (EIN, dann nach pulseSeconds automatisch AUS). */
    public static SwitchSchedule inching(UUID id, String switchId, int pulseSeconds) {
        return new SwitchSchedule(id, switchId, ScheduleType.INCHING, SwitchState.ON, true,
                null, Set.of(), null, null, null, pulseSeconds);
    }
}
