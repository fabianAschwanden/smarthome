package fabianaschwanden.smarthome.domain.port.out.energy;

import fabianaschwanden.smarthome.domain.model.energy.EnergySample;

import java.time.Instant;
import java.util.List;

/**
 * Getriebener Port: speichert und liest die Energie-Messpunkte (Zeitreihe). Der
 * Persistence-Adapter implementiert diesen Port; die Aggregation zu Buckets macht der
 * Application-Service, damit die Zeitzonen-Logik framework-frei bleibt.
 */
public interface EnergySampleRepository {

    /** Legt einen neuen Messpunkt ab. */
    void save(EnergySample sample);

    /** Messpunkte im Zeitfenster [from, to), aufsteigend nach Zeit. */
    List<EnergySample> between(Instant fromInclusive, Instant toExclusive);

    /** Löscht Messpunkte älter als {@code cutoff}; gibt die Anzahl gelöschter Zeilen zurück. */
    long deleteOlderThan(Instant cutoff);

    /** Gesamtzahl gespeicherter Messpunkte (für den Demo-Seeder). */
    long total();
}
