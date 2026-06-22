package fabianaschwanden.smarthome.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA-Entity der Alert-Einstellungen – eine einzige Zeile (Singleton, feste
 * {@link #SINGLETON_ID}). Lebt ausschliesslich im Persistence-Adapter.
 */
@Entity
@Table(name = "alert_settings")
public class AlertSettingsEntity {

    /** Es gibt genau einen Datensatz; dieser Schlüssel identifiziert ihn. */
    public static final String SINGLETON_ID = "default";

    @Id
    @Column(name = "id", length = 16)
    public String id;

    @Column(name = "enabled", nullable = false)
    public boolean enabled;

    @Column(name = "ntfy_topic", nullable = false, length = 128)
    public String ntfyTopic;
}
