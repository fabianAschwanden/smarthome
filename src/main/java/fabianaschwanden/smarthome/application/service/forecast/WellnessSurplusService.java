package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.application.config.WellnessSurplusConfig;
import fabianaschwanden.smarthome.domain.model.applianceschedule.ApplianceSchedule;
import fabianaschwanden.smarthome.domain.model.forecast.SurplusWindow;
import fabianaschwanden.smarthome.domain.port.in.applianceschedule.ManageApplianceSchedules;
import fabianaschwanden.smarthome.domain.port.in.forecast.NoRecommendationAvailable;
import fabianaschwanden.smarthome.domain.port.in.forecast.SurplusQuery;
import fabianaschwanden.smarthome.domain.port.in.forecast.WellnessSurplusPlan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Legt die Wellness-Heizung in ein erwartetes Überschussfenster.
 *
 * <p>Aufwärmen ist eine der wenigen Lasten im Haus, die sich ohne Komfortverlust
 * verschieben lässt: Wann das Wasser warm wird, merkt niemand – solange es warm ist,
 * wenn man hineinsteigt. Genau deshalb lohnt es sich, dafür auf die Sonne zu warten.
 *
 * <p>Der Dienst schaltet nichts selbst, sondern legt Aufträge in der
 * Wellness-Zeitsteuerung an – wie die Ladeempfehlung über die Batterie-Zeitsteuerung
 * geht und der Hitzeschutz über die der Storen.
 *
 * <p><b>Geregelt wird über die Soll-Temperatur, nicht über einen Schalter.</b> Die
 * Heizung eines Gecko-Spas lässt sich nicht ein- und ausschalten; sie ist dauerhaft
 * aktiv und folgt der Soll-Temperatur. Zu Fensterbeginn wird deshalb auf die
 * Überschusstemperatur gestellt, am Ende zurück auf die Grundtemperatur.
 *
 * <p><b>Am Fensterende wird auf die Grundtemperatur zurückgestellt – auch dann, wenn
 * jemand zwischendurch von Hand etwas anderes eingestellt hat.</b> Das ist die
 * unangenehme Seite der Sache und der Grund, warum es beim Knopfdruck bleibt und nicht
 * automatisch läuft.
 */
@ApplicationScoped
public class WellnessSurplusService implements WellnessSurplusPlan {

    private static final Logger LOG = Logger.getLogger(WellnessSurplusService.class);

    private final SurplusQuery surplus;
    private final ManageApplianceSchedules schedules;
    private final WellnessSurplusConfig config;

    @Inject
    public WellnessSurplusService(
            SurplusQuery surplus, ManageApplianceSchedules schedules, WellnessSurplusConfig config) {
        this.surplus = surplus;
        this.schedules = schedules;
        this.config = config;
    }

    @Override
    public List<ApplianceSchedule> applyWellnessSurplus() {
        SurplusWindow window = surplus.recommendation()
                .map(recommendation -> recommendation.window())
                .or(this::firstWindow)
                .orElseThrow(NoRecommendationAvailable::new);

        List<ApplianceSchedule> created = new ArrayList<>();
        for (WellnessSurplusConfig.Entry entry : config.appliances()) {
            created.add(schedules.save(
                    ApplianceSchedule.countdown(entry.id(), entry.surplusTemp(), window.from())));
            created.add(schedules.save(
                    ApplianceSchedule.countdown(entry.id(), entry.baseTemp(), window.to())));
        }
        LOG.infof("Wellness-Heizung ins Überschussfenster gelegt: %s bis %s, erwartet %.1f kWh",
                window.from(), window.to(), window.expectedKwh());
        return created;
    }

    /**
     * Ohne Ladeempfehlung taugt das erste Überschussfenster trotzdem: Die Empfehlung
     * gilt der Batterie und verlangt deren Schwellen; zum Aufheizen reicht auch ein
     * kleineres Fenster.
     */
    private Optional<SurplusWindow> firstWindow() {
        return surplus.windows().stream().findFirst();
    }
}
