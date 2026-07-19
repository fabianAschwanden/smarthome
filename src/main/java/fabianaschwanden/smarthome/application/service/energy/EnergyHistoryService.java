package fabianaschwanden.smarthome.application.service.energy;

import fabianaschwanden.smarthome.domain.model.energy.EnergyBucket;
import fabianaschwanden.smarthome.domain.model.energy.EnergyHistory;
import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
import fabianaschwanden.smarthome.domain.model.energy.HistoryRange;
import fabianaschwanden.smarthome.domain.port.in.energy.EnergyHistoryQuery;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySampleRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Application-Service: aggregiert die aufgezeichneten {@link EnergySample}s zu einer
 * kWh-Zeitreihe (Tag = Stunden-Buckets, Woche/Monat = Tages-Buckets).
 *
 * <p>Energie je Bucket = Ø-Leistung der Messpunkte im Bucket × tatsächlich verstrichene
 * Bucket-Dauer / 1000. Dieser Ansatz ist unabhängig von der Sample-Dichte und behandelt
 * den angebrochenen aktuellen Bucket korrekt (nur die bisher verstrichene Zeit).
 * Bucket-Grenzen werden zeitzonenbewusst (und damit DST-korrekt) gebildet.
 */
@ApplicationScoped
public class EnergyHistoryService implements EnergyHistoryQuery {

    private final EnergySampleRepository repository;
    private final Clock clock;
    private final ZoneId zone;

    @Inject
    public EnergyHistoryService(
            EnergySampleRepository repository,
            @ConfigProperty(name = "energy.history.zone", defaultValue = "Europe/Zurich") String zone) {
        this(repository, Clock.systemUTC(), ZoneId.of(zone));
    }

    // Sichtbar fürs Testen (deterministische Zeit/Zone).
    EnergyHistoryService(EnergySampleRepository repository, Clock clock, ZoneId zone) {
        this.repository = repository;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public EnergyHistory history(HistoryRange range) {
        Instant now = clock.instant();
        ZonedDateTime windowStart = windowStart(range, now);
        List<EnergySample> samples = repository.between(windowStart.toInstant(), now);

        Map<Instant, double[]> byBucket = accumulate(range, samples); // [pvSum, consSum, selfUseSum, count]

        List<EnergyBucket> buckets = new ArrayList<>();
        ZonedDateTime cursor = windowStart;
        while (!cursor.toInstant().isAfter(now)) {
            Instant start = cursor.toInstant();
            ZonedDateTime next = step(range, cursor);
            Instant effectiveEnd = min(next.toInstant(), now);
            double hours = Math.max(0, Duration.between(start, effectiveEnd).toMillis() / 3_600_000.0);

            double[] agg = byBucket.get(start);
            double pvKwh = 0;
            double consumptionKwh = 0;
            double selfUseKwh = 0;
            if (agg != null && agg[3] > 0) {
                pvKwh = (agg[0] / agg[3]) * hours / 1000.0;
                consumptionKwh = (agg[1] / agg[3]) * hours / 1000.0;
                selfUseKwh = (agg[2] / agg[3]) * hours / 1000.0;
            }
            buckets.add(new EnergyBucket(start, round(pvKwh), round(consumptionKwh), round(selfUseKwh)));
            cursor = next;
        }
        // Für den Tag die Leistungskurve mitgeben – auf Minuten-Auflösung verdichtet
        // (Mittelwert je Minute): feiner löst kein Chart auf, und die Response bleibt
        // klein, obwohl alle 10 s gesampelt wird. Die kWh-Aggregation oben nutzt
        // weiterhin alle Roh-Messpunkte. Woche/Monat brauchen nur die Tages-Buckets.
        List<EnergySample> curve = range == HistoryRange.DAY ? minuteCurve(samples) : List.of();
        return new EnergyHistory(range, buckets, curve);
    }

    /** Verdichtet Messpunkte auf einen je Minute (Mittelwert), zeitlich aufsteigend. */
    private static List<EnergySample> minuteCurve(List<EnergySample> samples) {
        List<EnergySample> curve = new ArrayList<>();
        Instant minute = null;
        double pvSum = 0;
        double consSum = 0;
        int n = 0;
        for (EnergySample s : samples) {
            Instant m = s.timestamp().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            if (minute != null && !m.equals(minute)) {
                curve.add(new EnergySample(minute, pvSum / n, consSum / n));
                pvSum = 0;
                consSum = 0;
                n = 0;
            }
            minute = m;
            pvSum += s.pvWatt();
            consSum += s.consumptionWatt();
            n++;
        }
        if (minute != null) {
            curve.add(new EnergySample(minute, pvSum / n, consSum / n));
        }
        return curve;
    }

    private Map<Instant, double[]> accumulate(HistoryRange range, List<EnergySample> samples) {
        Map<Instant, double[]> byBucket = new HashMap<>();
        for (EnergySample s : samples) {
            Instant key = bucketStart(range, s.timestamp());
            double[] agg = byBucket.computeIfAbsent(key, k -> new double[4]);
            agg[0] += s.pvWatt();
            agg[1] += s.consumptionWatt();
            agg[2] += Math.min(s.pvWatt(), s.consumptionWatt()); // Eigennutzung
            agg[3] += 1;
        }
        return byBucket;
    }

    /** Beginn des Bereichs: heute 00:00 (DAY) bzw. N-1 Tage zurück ab heute 00:00 (WEEK/MONTH). */
    private ZonedDateTime windowStart(HistoryRange range, Instant now) {
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate startDay = range == HistoryRange.DAY ? today : today.minusDays(range.bucketCount() - 1L);
        return startDay.atStartOfDay(zone);
    }

    private Instant bucketStart(HistoryRange range, Instant ts) {
        ZonedDateTime z = ts.atZone(zone);
        return range == HistoryRange.DAY
                ? z.truncatedTo(java.time.temporal.ChronoUnit.HOURS).toInstant()
                : z.toLocalDate().atStartOfDay(zone).toInstant();
    }

    private ZonedDateTime step(HistoryRange range, ZonedDateTime from) {
        return range == HistoryRange.DAY ? from.plusHours(1) : from.plusDays(1);
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    /** Auf 3 Nachkommastellen (Wh-Auflösung) runden – reicht für die Anzeige. */
    private static double round(double kwh) {
        return Math.round(kwh * 1000.0) / 1000.0;
    }
}
