package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.model.applianceschedule.ApplianceSchedule;
import fabianaschwanden.smarthome.domain.model.forecast.SurplusWindow;
import fabianaschwanden.smarthome.domain.port.in.applianceschedule.ManageApplianceSchedules;
import fabianaschwanden.smarthome.domain.port.in.forecast.NoRecommendationAvailable;
import fabianaschwanden.smarthome.domain.port.in.forecast.SurplusQuery;
import fabianaschwanden.smarthome.domain.port.in.forecast.WellnessSurplusPlan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
 * <p><b>Ausgeschaltet wird am Fensterende auch dann, wenn die Heizung schon vorher
 * lief.</b> Das ist die unangenehme Seite der Sache und der Grund, warum es beim
 * Knopfdruck bleibt und nicht automatisch läuft: Wer die Heizung von Hand angestellt
 * hat, wird sie so am Abend abgestellt finden.
 */
@ApplicationScoped
public class WellnessSurplusService implements WellnessSurplusPlan {

    private static final Logger LOG = Logger.getLogger(WellnessSurplusService.class);

    private final SurplusQuery surplus;
    private final ManageApplianceSchedules schedules;
    private final List<String> applianceIds;

    @Inject
    public WellnessSurplusService(
            SurplusQuery surplus,
            ManageApplianceSchedules schedules,
            @ConfigProperty(name = "forecast.wellness.appliance-ids") List<String> applianceIds) {
        this.surplus = surplus;
        this.schedules = schedules;
        this.applianceIds = List.copyOf(applianceIds);
    }

    @Override
    public List<ApplianceSchedule> applyWellnessSurplus() {
        SurplusWindow window = surplus.recommendation()
                .map(recommendation -> recommendation.window())
                .or(this::firstWindow)
                .orElseThrow(NoRecommendationAvailable::new);

        List<ApplianceSchedule> created = new ArrayList<>();
        for (String applianceId : applianceIds) {
            created.add(schedules.save(ApplianceSchedule.countdown(
                    applianceId, ApplianceFunction.HEATER, FunctionState.ON, window.from())));
            created.add(schedules.save(ApplianceSchedule.countdown(
                    applianceId, ApplianceFunction.HEATER, FunctionState.OFF, window.to())));
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
