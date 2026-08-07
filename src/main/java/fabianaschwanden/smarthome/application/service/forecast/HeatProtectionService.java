package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.domain.model.coverschedule.CoverSchedule;
import fabianaschwanden.smarthome.domain.model.coverschedule.CoverScheduleType;
import fabianaschwanden.smarthome.domain.model.forecast.HeatProtectionWindow;
import fabianaschwanden.smarthome.domain.model.sensor.Sensor;
import fabianaschwanden.smarthome.domain.port.in.coverschedule.ManageCoverSchedules;
import fabianaschwanden.smarthome.domain.port.in.forecast.ApplyHeatProtection;
import fabianaschwanden.smarthome.domain.port.in.forecast.HeatProtectionQuery;
import fabianaschwanden.smarthome.domain.port.in.forecast.NoHeatProtectionAvailable;
import fabianaschwanden.smarthome.domain.port.in.forecast.PvForecastQuery;
import fabianaschwanden.smarthome.domain.port.in.sensor.ReadSensors;
import fabianaschwanden.smarthome.domain.service.forecast.HeatProtectionPlanner;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Empfiehlt, die Storen gegen die Sommerhitze zu fahren, und übernimmt die Empfehlung
 * auf Wunsch als Zeitsteuerung.
 *
 * <p>Der Dienst fährt nichts selbst: Er legt über {@link ManageCoverSchedules}
 * (Use Case 5) Countdowns an. Ein zweiter Schaltpfad neben der bestehenden
 * Zeitsteuerung wäre eine Duplikation – derselbe Grundsatz wie bei der Ladeempfehlung.
 *
 * <p><b>Zugefahren wird auf Geräte-Position 2, nicht auf 0.</b> Das entspricht «98 % zu»
 * in der Anzeige: Ein Spalt bleibt offen. Ganz unten sitzt der Behang auf dem Anschlag
 * auf, und der Raum wird völlig dunkel – für Hitzeschutz braucht es beides nicht.
 */
@ApplicationScoped
public class HeatProtectionService implements HeatProtectionQuery, ApplyHeatProtection {

    private static final Logger LOG = Logger.getLogger(HeatProtectionService.class);

    private final PvForecastQuery forecasts;
    private final ReadSensors sensors;
    private final ManageCoverSchedules schedules;
    private final HeatProtectionPlanner planner = new HeatProtectionPlanner();
    private final Clock clock;
    private final String sensorId;
    private final List<String> coverIds;
    private final double gtiThreshold;
    private final double indoorTempThreshold;
    private final Duration minDuration;
    private final int shadedPosition;
    private final int openPosition;

    @Inject
    public HeatProtectionService(
            PvForecastQuery forecasts,
            ReadSensors sensors,
            ManageCoverSchedules schedules,
            @ConfigProperty(name = "forecast.heat-protection.sensor-id") String sensorId,
            @ConfigProperty(name = "forecast.heat-protection.cover-ids") List<String> coverIds,
            @ConfigProperty(name = "forecast.heat-protection.gti-threshold") double gtiThreshold,
            @ConfigProperty(name = "forecast.heat-protection.indoor-temp") double indoorTempThreshold,
            @ConfigProperty(name = "forecast.heat-protection.min-duration") Duration minDuration,
            @ConfigProperty(name = "forecast.heat-protection.position") int shadedPosition,
            @ConfigProperty(name = "forecast.heat-protection.open-position") int openPosition) {
        this(forecasts, sensors, schedules, Clock.systemUTC(), sensorId, coverIds,
                gtiThreshold, indoorTempThreshold, minDuration, shadedPosition, openPosition);
    }

    // Sichtbar fürs Testen: feste Uhr, deterministische Fenster.
    HeatProtectionService(
            PvForecastQuery forecasts,
            ReadSensors sensors,
            ManageCoverSchedules schedules,
            Clock clock,
            String sensorId,
            List<String> coverIds,
            double gtiThreshold,
            double indoorTempThreshold,
            Duration minDuration,
            int shadedPosition,
            int openPosition) {
        this.forecasts = forecasts;
        this.sensors = sensors;
        this.schedules = schedules;
        this.clock = clock;
        this.sensorId = sensorId;
        this.coverIds = List.copyOf(coverIds);
        this.gtiThreshold = gtiThreshold;
        this.indoorTempThreshold = indoorTempThreshold;
        this.minDuration = minDuration;
        this.shadedPosition = shadedPosition;
        this.openPosition = openPosition;
    }

    @Override
    public Optional<HeatProtectionWindow> heatProtection() {
        Optional<Double> indoor = indoorTemp();
        if (indoor.isEmpty()) {
            // Ohne Innentemperatur fehlt die halbe Bedingung. Zu raten hiesse, an einem
            // kuehlen Apriltag die Storen zu schliessen, nur weil die Sonne scheint.
            return Optional.empty();
        }
        return forecasts.currentForecast().flatMap(forecast -> planner.plan(
                forecast, indoor.get(), clock.instant(),
                gtiThreshold, indoorTempThreshold, minDuration));
    }

    @Override
    public List<CoverSchedule> applyHeatProtection() {
        HeatProtectionWindow window = heatProtection().orElseThrow(NoHeatProtectionAvailable::new);

        List<CoverSchedule> created = new ArrayList<>();
        for (String coverId : coverIds) {
            // COUNTDOWN statt SCHEDULE: Das Fenster gilt fuer heute, morgen steht die
            // Sonne anders und die Wolken auch. Ein Countdown deaktiviert sich selbst.
            created.add(schedules.save(countdown(coverId, shadedPosition, window.from())));
            created.add(schedules.save(countdown(coverId, openPosition, window.to())));
        }
        LOG.infof("Hitzeschutz übernommen: %s bis %s auf Position %d (%d %% zu), %.0f W/m², innen %.1f °C",
                window.from(), window.to(), shadedPosition, 100 - shadedPosition,
                window.peakGti(), window.indoorTemp());
        return created;
    }

    private CoverSchedule countdown(String coverId, int position, java.time.Instant fireAt) {
        return new CoverSchedule(
                UUID.randomUUID(), coverId, CoverScheduleType.COUNTDOWN, position, true, null, null, fireAt);
    }

    /** Innentemperatur des konfigurierten Sensors; leer, wenn er fehlt oder nichts meldet. */
    private Optional<Double> indoorTemp() {
        return sensors.list().stream()
                .filter(sensor -> sensor.id().equals(sensorId))
                .filter(Sensor::online)
                .map(Sensor::temperature)
                .filter(temp -> temp > -100)
                .findFirst();
    }
}
