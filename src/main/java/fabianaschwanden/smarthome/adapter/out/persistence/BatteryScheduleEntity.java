package fabianaschwanden.smarthome.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * JPA-Entity der Batterie-Zeitsteuerung – lebt ausschliesslich im Persistence-Adapter.
 * Ein Tisch deckt SCHEDULE und COUNTDOWN ab (typ-spezifische Spalten nullable);
 * Mapping/Invarianten siehe Domänen-Record {@code BatterySchedule}.
 */
@Entity
@Table(name = "battery_schedule")
public class BatteryScheduleEntity {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String type;

    /** Relais-Aktion: ON/OFF. */
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
}
