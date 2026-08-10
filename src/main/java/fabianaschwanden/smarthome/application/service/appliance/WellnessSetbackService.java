package fabianaschwanden.smarthome.application.service.appliance;

import fabianaschwanden.smarthome.application.config.WellnessConfig;
import fabianaschwanden.smarthome.domain.port.in.appliance.ControlAppliances;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Senkt die Soll-Temperatur der Wellness-Anlagen am Abend ab.
 *
 * <p><b>Warum es das braucht:</b> Ein Gecko-Spa hält seinen Sollwert rund um die Uhr. Wer
 * ihn tagsüber auf Badetemperatur stellt, heizt ohne Zutun die ganze Nacht weiter – und
 * zwar aus dem Netz, weil dann keine Sonne mehr scheint. Die Absenkung beendet das zu
 * einer festen Uhrzeit.
 *
 * <p>Geprüft wird im Minutentakt statt per Cron, damit Absenkzeit und Fenster-Ende aus
 * <em>einer</em> Konfigurationsangabe stammen: Der Überschuss-Plan muss dieselbe Uhrzeit
 * kennen, um sein Fenster daran zu kappen. Zwei Quellen (Cron hier, Uhrzeit dort) würden
 * über kurz oder lang auseinanderlaufen.
 */
@ApplicationScoped
public class WellnessSetbackService {

    private static final Logger LOG = Logger.getLogger(WellnessSetbackService.class);

    private final ControlAppliances appliances;
    private final WellnessConfig config;
    private final Clock clock;

    /** Tag, an dem zuletzt abgesenkt wurde – höchstens einmal je Tag. */
    private LocalDate lastSetback;

    @Inject
    public WellnessSetbackService(ControlAppliances appliances, WellnessConfig config) {
        this(appliances, config, Clock.systemDefaultZone());
    }

    // Sichtbar fürs Testen: feste Uhr.
    WellnessSetbackService(ControlAppliances appliances, WellnessConfig config, Clock clock) {
        this.appliances = appliances;
        this.config = config;
        this.clock = clock;
    }

    @Scheduled(every = "{wellness.setback-check-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        if (today.equals(lastSetback) || now.isBefore(config.setbackTime())) {
            return;
        }
        lastSetback = today;
        for (WellnessConfig.Entry entry : config.appliances()) {
            try {
                appliances.setTargetTemperature(entry.id(), entry.nightTemp());
                LOG.infof("Abendabsenkung: %s -> %d °C", entry.id(), entry.nightTemp());
            } catch (RuntimeException e) {
                // Ein Fehlschlag darf die uebrigen Anlagen nicht mitreissen. Wiederholt
                // wird nicht: Das Nachhalten der Soll-Temperatur erledigt das ohnehin.
                LOG.warnf("Abendabsenkung fuer %s fehlgeschlagen: %s", entry.id(), e.getMessage());
            }
        }
    }
}
