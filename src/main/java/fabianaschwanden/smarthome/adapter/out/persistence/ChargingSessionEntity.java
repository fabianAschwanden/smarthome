package fabianaschwanden.smarthome.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA-Entity eines Ladevorgangs – lebt ausschliesslich im Persistence-Adapter.
 * {@code endedAt}, {@code watt} und {@code energyKwh} bleiben {@code null}, solange der
 * Vorgang läuft.
 */
@Entity
@Table(name = "charging_session")
public class ChargingSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "started_at", nullable = false)
    public Instant startedAt;

    @Column(name = "ended_at")
    public Instant endedAt;

    @Column(name = "watt")
    public Double watt;

    @Column(name = "energy_kwh")
    public Double energyKwh;
}
