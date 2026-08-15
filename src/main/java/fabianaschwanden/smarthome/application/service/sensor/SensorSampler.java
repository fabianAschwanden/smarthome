package fabianaschwanden.smarthome.application.service.sensor;

import fabianaschwanden.smarthome.domain.model.sensor.Sensor;
import fabianaschwanden.smarthome.domain.model.sensor.SensorSample;
import fabianaschwanden.smarthome.domain.port.in.sensor.ReadSensors;
import fabianaschwanden.smarthome.domain.port.out.sensor.SensorSampleRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Zeichnet Temperatur und Luftfeuchte periodisch auf und verdichtet alte Aufzeichnungen.
 *
 * <p><b>Warum neben Prometheus:</b> Dort stehen dieselben Werte, aber Prometheus kennt nur
 * <em>eine</em> Aufbewahrungsdauer je Instanz – keine Retention je Metrik und kein
 * Downsampling. «Nach einem Jahr nur noch stündlich» lässt sich so nicht ausdrücken.
 * Prometheus behält deshalb die feine Auflösung der letzten Wochen fürs Dashboard, diese
 * Aufzeichnung die Jahre.
 *
 * <p>Aufgezeichnet werden nur belastbare Werte. Ein offline gemeldeter Sensor oder ein
 * Platzhalter der Domäne erzeugt <b>keinen</b> Punkt – eine Lücke ist ehrlicher als eine
 * erfundene Zahl und in jeder Auswertung harmloser als ein Sprung auf 0 °C.
 */
@ApplicationScoped
public class SensorSampler {

    private static final Logger LOG = Logger.getLogger(SensorSampler.class);

    private final ReadSensors sensors;
    private final SensorSampleRepository repository;
    private final Clock clock;
    private final Duration rawWindow;

    @Inject
    public SensorSampler(
            ReadSensors sensors,
            SensorSampleRepository repository,
            @ConfigProperty(name = "sensor-history.raw-days") int rawDays) {
        this(sensors, repository, Clock.systemUTC(), rawDays);
    }

    // Sichtbar fürs Testen: feste Uhr.
    SensorSampler(ReadSensors sensors, SensorSampleRepository repository, Clock clock, int rawDays) {
        this.sensors = sensors;
        this.repository = repository;
        this.clock = clock;
        this.rawWindow = Duration.ofDays(rawDays);
    }

    @Scheduled(every = "{sensor-history.sample-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sample() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        for (Sensor sensor : sensors.list()) {
            if (!sensor.online() || sensor.temperature() <= Sensor.VALUE_UNKNOWN) {
                continue;
            }
            repository.save(new SensorSample(sensor.id(), now, sensor.temperature(), sensor.humidity()));
        }
    }

    /**
     * Verdichtet alles, was älter ist als {@code sensor-history.raw-days}, auf einen
     * Punkt je Stunde. Läuft täglich; ein zweiter Lauf am selben Tag findet nichts mehr,
     * weil die Verdichtung deterministisch den ersten Punkt der Stunde behält.
     */
    @Scheduled(cron = "{sensor-history.compact-cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void compact() {
        long removed = repository.compactOlderThan(clock.instant().minus(rawWindow));
        if (removed > 0) {
            LOG.infof("Sensor-Historie verdichtet: %d Messpunkte entfernt, %d verbleiben",
                    removed, repository.total());
        }
    }
}
