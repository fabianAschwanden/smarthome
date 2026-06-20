package fabianaschwanden.smarthome.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** JPA-Entity — lebt ausschliesslich im Persistence-Adapter (öffentliche Felder OK). */
@Entity
@Table(name = "item_image")
public class ItemImageEntity {

    /** Fachliche Item-ID (z. B. {@code stehlampe}) – ein Bild je Item. */
    @Id
    @Column(name = "item_id", length = 64)
    public String itemId;

    /** Data-URL {@code data:image/…;base64,…} – als {@code text} (entspricht Liquibase clob auf PG). */
    @Column(name = "data_url", nullable = false, columnDefinition = "text")
    public String dataUrl;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
