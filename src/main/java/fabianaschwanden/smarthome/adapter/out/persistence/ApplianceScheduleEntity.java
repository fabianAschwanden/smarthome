package fabianaschwanden.smarthome.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA-Entity eines Wellness-Schaltauftrags – lebt ausschliesslich im
 * Persistence-Adapter. Bedeutung siehe Domänen-Record {@code ApplianceSchedule}.
 */
@Entity
@Table(name = "appliance_schedule")
public class ApplianceScheduleEntity {

    @Id
    public UUID id;

    @Column(name = "appliance_id", nullable = false)
    public String applianceId;

    @Column(name = "function", nullable = false, length = 32)
    public String function;

    @Column(name = "state", nullable = false, length = 8)
    public String state;

    @Column(name = "fire_at", nullable = false)
    public Instant fireAt;

    @Column(nullable = false)
    public boolean enabled;
}
