package fabianaschwanden.smarthome.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * JPA-Entity der Lade-Automatik – eine einzige Zeile (Singleton, feste
 * {@link #SINGLETON_ID}). Lebt ausschliesslich im Persistence-Adapter.
 */
@Entity
@Table(name = "forecast_auto_apply")
public class AutoApplyStateEntity {

    public static final String SINGLETON_ID = "default";

    @Id
    @Column(name = "id", length = 16)
    public String id;

    @Column(name = "enabled", nullable = false)
    public boolean enabled;

    /** null, solange die Automatik noch nie gelaufen ist. */
    @Column(name = "last_run_day")
    public LocalDate lastRunDay;

    @Column(name = "last_outcome", length = 32)
    public String lastOutcome;

    @Column(name = "last_detail", length = 256)
    public String lastDetail;
}
