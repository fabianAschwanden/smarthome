package fabianaschwanden.smarthome.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA-Entity eines Energie-Messpunkts (Zeitreihe) – lebt ausschliesslich im
 * Persistence-Adapter. Mapping/Bedeutung siehe Domänen-Record {@code EnergySample}.
 * Fortlaufende technische ID (bigserial), da Messpunkte keine fachliche Identität haben.
 */
@Entity
@Table(name = "energy_sample")
public class EnergySampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "ts", nullable = false)
    public Instant ts;

    @Column(name = "pv_watt", nullable = false)
    public double pvWatt;

    @Column(name = "consumption_watt", nullable = false)
    public double consumptionWatt;
}
