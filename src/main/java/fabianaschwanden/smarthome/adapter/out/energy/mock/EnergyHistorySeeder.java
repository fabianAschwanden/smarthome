package fabianaschwanden.smarthome.adapter.out.energy.mock;

import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySampleRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Füllt die Energie-Historie beim Start mit synthetischen Stunden-Messpunkten der
 * letzten ~45 Tage, damit Tages-/Wochen-/Monatsverlauf sofort demonstrierbar sind.
 *
 * <p>Läuft NUR, wenn {@code energy.seed-demo-history=true} <b>und</b>
 * {@code smarthome.real-devices=false} (Mock-Betrieb) <b>und</b> die Tabelle leer ist –
 * im Realbetrieb füllt der echte {@code EnergySampler} die Historie, hier wird nichts
 * überschrieben.
 */
@ApplicationScoped
public class EnergyHistorySeeder {

    private static final Logger LOG = Logger.getLogger(EnergyHistorySeeder.class);
    private static final int DAYS = 45;

    private final EnergySampleRepository repository;
    private final boolean seed;
    private final boolean realDevices;
    private final ZoneId zone;

    @Inject
    public EnergyHistorySeeder(
            EnergySampleRepository repository,
            @ConfigProperty(name = "energy.seed-demo-history", defaultValue = "false") boolean seed,
            @ConfigProperty(name = "smarthome.real-devices", defaultValue = "false") boolean realDevices,
            @ConfigProperty(name = "energy.history.zone", defaultValue = "Europe/Zurich") String zone) {
        this.repository = repository;
        this.seed = seed;
        this.realDevices = realDevices;
        this.zone = ZoneId.of(zone);
    }

    void onStart(@Observes StartupEvent ev) {
        if (!seed || realDevices || repository.total() > 0) {
            return;
        }
        LocalDate today = Instant.now().atZone(zone).toLocalDate();
        int hourNow = Instant.now().atZone(zone).getHour();
        int written = 0;
        for (int d = DAYS; d >= 0; d--) {
            LocalDate day = today.minusDays(d);
            int lastHour = d == 0 ? hourNow : 23;
            double cloudiness = 0.55 + 0.45 * Math.abs(Math.sin(day.toEpochDay() * 1.3)); // 0.55..1.0
            for (int h = 0; h <= lastHour; h++) {
                Instant ts = day.atTime(h, 0).atZone(zone).toInstant();
                repository.save(new EnergySample(ts, pvWatt(h, cloudiness), consumptionWatt(h)));
                written++;
            }
        }
        LOG.infof("Demo-Energie-Historie geseedet: %d synthetische Messpunkte (%d Tage).", written, DAYS);
    }

    /** Glockenförmiger PV-Tagesbogen (Peak ~5 kW um 13 Uhr), 0 nachts, mal Bewölkungsfaktor. */
    private static double pvWatt(int hour, double cloudiness) {
        if (hour < 6 || hour > 21) {
            return 0;
        }
        double bell = Math.exp(-Math.pow(hour - 13.0, 2) / (2 * 3.0 * 3.0));
        return Math.round(5200 * bell * cloudiness);
    }

    /** Grundlast ~250 W plus Morgen- und Abendspitze. */
    private static double consumptionWatt(int hour) {
        double base = 250;
        if (hour >= 7 && hour <= 9) {
            base += 400;
        }
        if (hour >= 18 && hour <= 22) {
            base += 650;
        }
        return base;
    }
}
