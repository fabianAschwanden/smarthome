package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.application.config.WellnessConfig;
import fabianaschwanden.smarthome.domain.model.applianceschedule.ApplianceSchedule;
import fabianaschwanden.smarthome.domain.model.forecast.SurplusWindow;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;
import fabianaschwanden.smarthome.domain.port.in.appliance.ControlAppliances;
import fabianaschwanden.smarthome.domain.port.in.applianceschedule.ManageApplianceSchedules;
import fabianaschwanden.smarthome.domain.port.in.batteryschedule.ManageBatterySchedules;
import fabianaschwanden.smarthome.domain.port.in.forecast.NoRecommendationAvailable;
import fabianaschwanden.smarthome.domain.port.in.forecast.SurplusQuery;
import fabianaschwanden.smarthome.domain.port.in.forecast.WellnessSurplusPlan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
 *
 * <p><b>Heizen und erzwungenes Laden schliessen sich aus.</b> Ein Batterie-Countdown
 * setzt den Manuell-Modus und lädt unabhängig davon, ob gerade wirklich Überschuss da
 * ist. Zusammen mit der Whirlpool-Heizung zöge das mehr, als die Anlage liefert – der
 * Rest käme aus dem Netz, und damit wäre der Zweck der Übung verfehlt. Wird die Heizung
 * eingeplant, werden anstehende Ladeaufträge deshalb abgeschaltet.
 *
 * <p>Die Batterie lädt dadurch nicht weniger, sondern anders: Im Automatik-Modus regelt
 * der SMARTFOX nach dem tatsächlichen Überschuss und nimmt sich, was die Heizung übrig
 * lässt.
 */
@ApplicationScoped
public class WellnessSurplusService implements WellnessSurplusPlan {

    private static final Logger LOG = Logger.getLogger(WellnessSurplusService.class);

    private final SurplusQuery surplus;
    private final ManageApplianceSchedules schedules;
    private final ManageBatterySchedules batterySchedules;
    private final ControlAppliances appliances;
    private final WellnessConfig config;
    private final Clock clock;

    @Inject
    public WellnessSurplusService(
            SurplusQuery surplus,
            ManageApplianceSchedules schedules,
            ManageBatterySchedules batterySchedules,
            ControlAppliances appliances,
            WellnessConfig config) {
        this(surplus, schedules, batterySchedules, appliances, config, Clock.systemDefaultZone());
    }

    // Sichtbar fürs Testen: feste Uhr und Zone.
    WellnessSurplusService(
            SurplusQuery surplus,
            ManageApplianceSchedules schedules,
            ManageBatterySchedules batterySchedules,
            ControlAppliances appliances,
            WellnessConfig config,
            Clock clock) {
        this.surplus = surplus;
        this.schedules = schedules;
        this.batterySchedules = batterySchedules;
        this.appliances = appliances;
        this.config = config;
        this.clock = clock;
    }

    @Override
    public List<ApplianceSchedule> applyWellnessSurplus() {
        SurplusWindow window = surplus.recommendation()
                .map(recommendation -> recommendation.window())
                .or(this::firstWindow)
                .orElseThrow(NoRecommendationAvailable::new);

        // Das Fenster endet spaetestens mit der Abendabsenkung. Ohne diese Kappung wuerde
        // ein spaeter endendes Fenster die Temperatur nach der Absenkung wieder anheben -
        // und der Whirlpool heizte doch in den Abend hinein.
        Instant setback = setbackInstant(window.from());
        boolean cappedByEvening = window.to().isAfter(setback);
        Instant end = cappedByEvening ? setback : window.to();

        List<ApplianceSchedule> created = new ArrayList<>();
        for (WellnessConfig.Entry entry : config.appliances()) {
            if (!appliances.isActive(entry.id())) {
                // Eine stillgelegte Anlage bekommt keinen Heizauftrag - er wuerde beim
                // Faelligwerden ohnehin verworfen und stuende bis dahin nur in der Liste.
                LOG.infof("Wellness-Ueberschuss: %s ist deaktiviert, nicht eingeplant", entry.id());
                continue;
            }
            int endTemp = cappedByEvening ? entry.nightTemp() : entry.baseTemp();
            created.add(schedules.save(
                    ApplianceSchedule.countdown(entry.id(), entry.surplusTemp(), window.from())));
            created.add(schedules.save(ApplianceSchedule.countdown(entry.id(), endTemp, end)));
        }
        int stopped = stopForcedCharging(window.from(), end);
        LOG.infof("Wellness-Heizung ins Überschussfenster gelegt: %s bis %s, erwartet %.1f kWh"
                        + (stopped > 0 ? " (%d Ladeauftrag/-aufträge abgeschaltet)" : ""),
                window.from(), window.to(), window.expectedKwh(), stopped);
        return created;
    }

    /** Die Absenkzeit des Tages, an dem das Fenster beginnt. */
    private Instant setbackInstant(Instant windowStart) {
        ZoneId zone = clock.getZone();
        return windowStart.atZone(zone).toLocalDate().atTime(config.setbackTime()).atZone(zone).toInstant();
    }

    /**
     * Schaltet Ladeaufträge ab, die im Heizfenster feuern würden.
     *
     * <p>Nur Countdowns: Eine wiederkehrende Regel gehört dem Betreiber, sie hier still
     * zu deaktivieren wäre ein Übergriff. Sie setzt allerdings ebenfalls den
     * Manuell-Modus – wer beides hat, muss selbst entscheiden.
     *
     * @return wie viele Aufträge abgeschaltet wurden
     */
    private int stopForcedCharging(Instant from, Instant to) {
        int stopped = 0;
        for (BatterySchedule schedule : batterySchedules.all()) {
            if (!schedule.enabled() || schedule.fireAt() == null) {
                continue;
            }
            if (!schedule.fireAt().isBefore(from) && schedule.fireAt().isBefore(to)) {
                batterySchedules.setEnabled(schedule.id(), false);
                LOG.infof("Ladeauftrag %s abgeschaltet - der Whirlpool heizt in diesem Fenster",
                        schedule.id());
                stopped++;
            }
        }
        return stopped;
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
