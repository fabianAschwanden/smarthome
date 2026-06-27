package fabianaschwanden.smarthome.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * JPA-Entity der Storen-Zeitsteuerung – lebt ausschliesslich im Persistence-Adapter.
 * Ein Tisch deckt SCHEDULE und COUNTDOWN ab (typ-spezifische Spalten nullable);
 * Mapping/Invarianten siehe Domänen-Record {@code CoverSchedule}.
 */
@Entity
@Table(name = "cover_schedule")
public class CoverScheduleEntity {

    @Id
    public UUID id;

    @Column(name = "cover_id", nullable = false)
    public String coverId;

    @Column(nullable = false)
    public String type;

    /** Zielposition (0 = zu, 100 = offen). */
    @Column(name = "position", nullable = false)
    public int position;

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
}
