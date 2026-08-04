package fabianaschwanden.smarthome.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * JPA-Entity des gelernten Anlagenprofils – lebt ausschliesslich im Persistence-Adapter.
 * Bedeutung siehe Domänen-Record {@code PlantProfile}.
 *
 * <p>Die {@code id} ist bewusst fest ({@link #SINGLETON_ID}) statt generiert: Es gibt
 * genau ein Profil, neu lernen überschreibt es. So ist der Upsert ein simples
 * „laden oder anlegen" und es kann strukturell keine zweite Zeile entstehen.
 *
 * <p>{@code profile} hält die Faktoren als JSON-Snapshot. Sie sind ein zusammengehöriger
 * Wert, der nur als Ganzes gelesen und geschrieben wird – 24 Spalten wären hier reine
 * Zeremonie.
 */
@Entity
@Table(name = "plant_profile")
public class PlantProfileEntity {

    /** Es gibt genau eine Zeile – diese hier. */
    public static final long SINGLETON_ID = 1L;

    @Id
    public Long id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile", nullable = false)
    public String profile;

    @Column(name = "learned_at", nullable = false)
    public Instant learnedAt;
}
