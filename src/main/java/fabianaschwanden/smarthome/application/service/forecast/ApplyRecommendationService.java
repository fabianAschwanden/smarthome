package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatteryScheduleType;
import fabianaschwanden.smarthome.domain.model.forecast.ChargeRecommendation;
import fabianaschwanden.smarthome.domain.model.applianceschedule.ApplianceSchedule;
import fabianaschwanden.smarthome.domain.port.in.applianceschedule.ManageApplianceSchedules;
import fabianaschwanden.smarthome.domain.port.in.batteryschedule.ManageBatterySchedules;
import fabianaschwanden.smarthome.domain.port.in.forecast.ApplyRecommendation;
import fabianaschwanden.smarthome.domain.port.in.forecast.ChargingBlockedByWellness;
import fabianaschwanden.smarthome.domain.port.in.forecast.NoRecommendationAvailable;
import fabianaschwanden.smarthome.domain.port.in.forecast.SurplusQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Übernimmt die aktuelle Ladeempfehlung als Batterie-Zeitplan.
 *
 * <p><b>Nicht, während der Whirlpool heizt.</b> Ein Countdown setzt den Manuell-Modus und
 * lädt unabhängig vom tatsächlichen Überschuss; zusammen mit der Heizung käme der Rest
 * aus dem Netz. Steht eine Wellness-Heizung an, wird deshalb kein Ladeauftrag angelegt –
 * die Batterie lädt in dieser Zeit über den Automatik-Modus aus dem, was übrig bleibt.
 *
 * <p>Der Dienst schaltet nichts: Er legt über {@link ManageBatterySchedules} (Use Case 14)
 * einen einmaligen Countdown an, der das Relais zum Fensterbeginn einschaltet. Die
 * Ausführung – Manuell-Modus setzen und danach zurück – gehört vollständig dem
 * bestehenden Use Case. Ein zweiter Schaltpfad wäre eine Duplikation der Geräte-Automatik
 * (SPEC §1).
 */
@ApplicationScoped
public class ApplyRecommendationService implements ApplyRecommendation {

    private static final Logger LOG = Logger.getLogger(ApplyRecommendationService.class);

    private final SurplusQuery surplus;
    private final ManageBatterySchedules schedules;
    private final ManageApplianceSchedules wellnessSchedules;

    @Inject
    public ApplyRecommendationService(
            SurplusQuery surplus,
            ManageBatterySchedules schedules,
            ManageApplianceSchedules wellnessSchedules) {
        this.surplus = surplus;
        this.schedules = schedules;
        this.wellnessSchedules = wellnessSchedules;
    }

    /**
     * Heizt der Whirlpool in genau diesem Fenster?
     *
     * <p>Geprüft wird der Zeitraum, nicht bloss «ist überhaupt etwas geplant»: Eine
     * Heizung, die für morgen eingeplant ist, darf das Laden heute nicht verhindern.
     * Beide Fenster stammen aus derselben Prognose, der Heizbeginn fällt deshalb genau
     * auf den Fensteranfang.
     */
    boolean wellnessHeatingIn(Instant from, Instant to) {
        return wellnessSchedules.all().stream()
                .filter(ApplianceSchedule::enabled)
                .anyMatch(s -> !s.fireAt().isBefore(from) && !s.fireAt().isAfter(to));
    }

    @Override
    public BatterySchedule apply() {
        Optional<ChargeRecommendation> recommendation = surplus.recommendation();
        if (recommendation.isEmpty()) {
            throw new NoRecommendationAvailable();
        }

        var window = recommendation.get().window();
        if (wellnessHeatingIn(window.from(), window.to())) {
            throw new ChargingBlockedByWellness();
        }
        // COUNTDOWN statt SCHEDULE: Die Empfehlung gilt für dieses eine Fenster, nicht
        // als wiederkehrende Regel – morgen sieht die Prognose anders aus. Ein
        // Countdown deaktiviert sich nach dem Auslösen selbst.
        BatterySchedule schedule = new BatterySchedule(
                UUID.randomUUID(),
                BatteryScheduleType.COUNTDOWN,
                RelayState.ON,
                true,
                null,
                null,
                window.from());
        BatterySchedule saved = schedules.save(schedule);
        LOG.infof("Ladeempfehlung übernommen: %s bis %s, erwartet %.1f kWh",
                window.from(), window.to(), window.expectedKwh());
        return saved;
    }
}
