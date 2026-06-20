package fabianaschwanden.smarthome.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * JPA-Entity der Zeitsteuerungs-Regel – lebt ausschliesslich im Persistence-Adapter.
 * Ein Tisch deckt alle Typen ab; je Typ sind unterschiedliche Spalten befüllt
 * (Mapping/Invarianten siehe Domänen-Record {@code SwitchSchedule}).
 */
@Entity
@Table(name = "switch_schedule")
public class SwitchScheduleEntity {

    @Id
    public UUID id;

    @Column(name = "switch_id", nullable = false)
    public String switchId;

    @Column(nullable = false)
    public String type;

    @Column(name = "action", nullable = false)
    public String action;

    @Column(nullable = false)
    public boolean enabled;

    /** SCHEDULE: Uhrzeit. */
    @Column(name = "at_time")
    public LocalTime atTime;

    /** SCHEDULE: Wochentage als CSV (z. B. "MONDAY,TUESDAY"); leer = täglich. */
    @Column(name = "weekdays")
    public String weekdays;

    /** COUNTDOWN: Auslösezeitpunkt. */
    @Column(name = "fire_at")
    public Instant fireAt;

    /** RANDOM: Zeitfenster. */
    @Column(name = "window_start")
    public LocalTime windowStart;

    @Column(name = "window_end")
    public LocalTime windowEnd;

    /** INCHING: Impulsdauer in Sekunden. */
    @Column(name = "pulse_seconds")
    public Integer pulseSeconds;
}
