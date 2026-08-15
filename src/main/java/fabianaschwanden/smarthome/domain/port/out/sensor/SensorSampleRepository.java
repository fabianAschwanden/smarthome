package fabianaschwanden.smarthome.domain.port.out.sensor;

import fabianaschwanden.smarthome.domain.model.sensor.SensorSample;

import java.time.Instant;
import java.util.List;

/** Getriebener Port: die aufgezeichneten Messpunkte der Umweltsensoren. */
public interface SensorSampleRepository {

    void save(SensorSample sample);

    /** Messpunkte im Zeitfenster [from, to), aufsteigend nach Zeit. */
    List<SensorSample> between(Instant fromInclusive, Instant toExclusive);

    /**
     * Verdichtet alles vor {@code cutoff} auf einen Messpunkt je Stunde und Sensor –
     * behalten wird der erste der Stunde.
     *
     * @return wie viele Punkte dabei entfernt wurden
     */
    long compactOlderThan(Instant cutoff);

    /** Gesamtzahl gespeicherter Messpunkte. */
    long total();
}
