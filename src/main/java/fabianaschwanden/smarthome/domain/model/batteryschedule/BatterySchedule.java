package fabianaschwanden.smarthome.domain.model.batteryschedule;

import fabianaschwanden.smarthome.domain.model.battery.RelayState;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Zeitsteuerungs-Regel für das Batterie-Lade-Relais. Zwei Typen:
 * {@link BatteryScheduleType#SCHEDULE} (feste Uhrzeit, optional Wochentage) und
 * {@link BatteryScheduleType#COUNTDOWN} (einmaliger Zeitpunkt). Die Aktion
 * ({@link RelayState}) schaltet das Relais EIN/AUS – die Ausführung versetzt die
 * Batterie dabei in den MANUAL-Modus (sonst überschreibt die Auto-Logik den Zustand).
 *
 * <p>Immutable {@code record}; Invarianten je Typ im Compact-Constructor.
 */
public record BatterySchedule(
        UUID id,
        BatteryScheduleType type,
        RelayState action,
        boolean enabled,
        LocalTime time,
        Set<DayOfWeek> weekdays,
        Instant fireAt) {

    public BatterySchedule {
        if (id == null) {
            throw new IllegalArgumentException("id darf nicht null sein");
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
        }
    }

    public BatterySchedule withEnabled(boolean newEnabled) {
        return new BatterySchedule(id, type, action, newEnabled, time, weekdays, fireAt);
    }

    /** SCHEDULE: gilt die Regel an diesem Wochentag? (leere Menge = täglich) */
    public boolean appliesOn(DayOfWeek day) {
        return weekdays.isEmpty() || weekdays.contains(day);
    }

    public Set<DayOfWeek> weekdays() {
        return Collections.unmodifiableSet(weekdays);
    }

    /** Fabrik: wiederkehrender Zeitplan. */
    public static BatterySchedule schedule(UUID id, RelayState action, LocalTime time, Set<DayOfWeek> weekdays) {
        return new BatterySchedule(id, BatteryScheduleType.SCHEDULE, action, true, time, weekdays, null);
    }

    /** Fabrik: einmaliger Countdown. */
    public static BatterySchedule countdown(UUID id, RelayState action, Instant fireAt) {
        return new BatterySchedule(id, BatteryScheduleType.COUNTDOWN, action, true, null, Set.of(), fireAt);
    }
}
