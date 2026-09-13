package fabianaschwanden.smarthome.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA-Entity des Ohne-Sonne-Ausschalters – eine einzige Zeile (Singleton, feste
 * {@link #SINGLETON_ID}). Lebt ausschliesslich im Persistence-Adapter.
 */
@Entity
@Table(name = "battery_sun_guard")
public class SunGuardEntity {

    public static final String SINGLETON_ID = "default";

    @Id
    @Column(name = "id", length = 16)
    public String id;

    @Column(name = "enabled", nullable = false)
    public boolean enabled;

    /** Ob seit dem letzten Abschalten wieder Sonne gesehen wurde. */
    @Column(name = "armed", nullable = false)
    public boolean armed;

    /** null, solange der Wächter noch nie abgeschaltet hat. */
    @Column(name = "last_tripped_at")
    public Instant lastTrippedAt;
}
