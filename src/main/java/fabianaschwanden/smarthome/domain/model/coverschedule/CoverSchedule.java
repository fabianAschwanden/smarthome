package fabianaschwanden.smarthome.domain.model.coverschedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Zeitsteuerungs-Regel für eine Store. Zwei Typen:
 * {@link CoverScheduleType#SCHEDULE} (feste Uhrzeit, optional Wochentage) und
 * {@link CoverScheduleType#COUNTDOWN} (einmaliger Zeitpunkt). Die Aktion ist eine
 * Zielposition (0 = zu, 100 = offen), auf die die Store gefahren wird.
 *
 * <p>Immutable {@code record}; Invarianten je Typ im Compact-Constructor.
 */
public record CoverSchedule(
        UUID id,
        String coverId,
        CoverScheduleType type,
        int position,
        boolean enabled,
        LocalTime time,
        Set<DayOfWeek> weekdays,
        Instant fireAt) {

    public CoverSchedule {
        if (id == null) {
            throw new IllegalArgumentException("id darf nicht null sein");
        }
        if (coverId == null || coverId.isBlank()) {
            throw new IllegalArgumentException("coverId darf nicht leer sein");
        }
        if (type == null) {
            throw new IllegalArgumentException("type darf nicht null sein");
        }
        if (position < 0 || position > 100) {
            throw new IllegalArgumentException("position muss zwischen 0 und 100 liegen");
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
        }
    }

    public CoverSchedule withEnabled(boolean newEnabled) {
        return new CoverSchedule(id, coverId, type, position, newEnabled, time, weekdays, fireAt);
    }

    /** SCHEDULE: gilt die Regel an diesem Wochentag? (leere Menge = täglich) */
    public boolean appliesOn(DayOfWeek day) {
        return weekdays.isEmpty() || weekdays.contains(day);
    }

    public Set<DayOfWeek> weekdays() {
        return Collections.unmodifiableSet(weekdays);
    }

    /** Fabrik: wiederkehrender Zeitplan. */
    public static CoverSchedule schedule(
            UUID id, String coverId, int position, LocalTime time, Set<DayOfWeek> weekdays) {
        return new CoverSchedule(id, coverId, CoverScheduleType.SCHEDULE, position, true, time, weekdays, null);
    }

    /** Fabrik: einmaliger Countdown. */
    public static CoverSchedule countdown(UUID id, String coverId, int position, Instant fireAt) {
        return new CoverSchedule(id, coverId, CoverScheduleType.COUNTDOWN, position, true, null, Set.of(), fireAt);
    }
}
