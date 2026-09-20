package fabianaschwanden.smarthome.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA-Entity einer stillgelegten Anlage – eine Zeile je deaktivierter Anlage, keine für
 * aktive. Lebt ausschliesslich im Persistence-Adapter.
 */
@Entity
@Table(name = "appliance_deactivation")
public class ApplianceDeactivationEntity {

    @Id
    @Column(name = "appliance_id", length = 64)
    public String applianceId;

    @Column(name = "deactivated_at", nullable = false)
    public Instant deactivatedAt;
}
