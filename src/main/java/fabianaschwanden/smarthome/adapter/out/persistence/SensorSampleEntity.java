package fabianaschwanden.smarthome.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA-Entity eines aufgezeichneten Sensor-Messpunkts – lebt ausschliesslich im
 * Persistence-Adapter. Bedeutung siehe Domänen-Record {@code SensorSample}.
 * Fortlaufende technische ID, da Messpunkte keine fachliche Identität haben.
 */
@Entity
@Table(name = "sensor_sample")
public class SensorSampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "sensor_id", nullable = false)
    public String sensorId;

    @Column(name = "ts", nullable = false)
    public Instant ts;

    @Column(name = "temperature", nullable = false)
    public double temperature;

    @Column(name = "humidity", nullable = false)
    public int humidity;
}
