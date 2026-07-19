package fabianaschwanden.smarthome.application.service.energy;

import fabianaschwanden.smarthome.domain.model.energy.EnergyBucket;
import fabianaschwanden.smarthome.domain.model.energy.EnergyHistory;
import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
import fabianaschwanden.smarthome.domain.model.energy.HistoryRange;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySampleRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyHistoryServiceTest {

    // 2026-07-18 12:30 UTC = 14:30 Europe/Zurich (Sommerzeit +2).
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-18T12:30:00Z"), ZoneOffset.UTC);
    private final ZoneId zone = ZoneId.of("Europe/Zurich");
    private final FakeRepo repo = new FakeRepo();
    private final EnergyHistoryService service = new EnergyHistoryService(repo, clock, zone);

    @Test
    void tagesverlaufBildetStundenBucketsBisZurAktuellenStunde() {
        // Hour 0 (00:00 Zurich) bis Hour 14 (14:00) -> 15 Buckets.
        EnergyHistory history = service.history(HistoryRange.DAY);
        assertEquals(HistoryRange.DAY, history.range());
        assertEquals(15, history.buckets().size());
    }

    @Test
    void mitteltLeistungImVollenStundenBucketZuKwh() {
        // 10:00 Zurich = 08:00 UTC. Zwei Messpunkte, Mittel PV 4500 W -> 4.5 kWh in der vollen Stunde.
        repo.add("2026-07-18T08:00:00Z", 4000, 300);
        repo.add("2026-07-18T08:20:00Z", 5000, 300);

        EnergyBucket tenAm = bucketAt(service.history(HistoryRange.DAY), "2026-07-18T08:00:00Z");
        assertEquals(4.5, tenAm.pvKwh(), 1e-9);
        assertEquals(0.3, tenAm.consumptionKwh(), 1e-9);
        // Eigennutzung = min(pv, verbrauch) = Verbrauch, solange PV drüber liegt.
        assertEquals(0.3, tenAm.selfUseKwh(), 1e-9);
    }

    @Test
    void eigennutzungIstMinimumAusPvUndVerbrauch() {
        // Nachtstunde: PV 0, Verbrauch 600 W -> Eigennutzung 0.
        repo.add("2026-07-18T01:00:00Z", 0, 600);

        EnergyBucket night = bucketAt(service.history(HistoryRange.DAY), "2026-07-18T01:00:00Z");
        assertEquals(0.6, night.consumptionKwh(), 1e-9);
        assertEquals(0.0, night.selfUseKwh(), 1e-9);
    }

    @Test
    void tagesverlaufEnthaeltLeistungskurveNurFuerDay() {
        repo.add("2026-07-18T08:00:00Z", 4000, 300);
        repo.add("2026-07-18T09:00:00Z", 5000, 400);

        assertEquals(2, service.history(HistoryRange.DAY).samples().size());
        assertEquals(0, service.history(HistoryRange.WEEK).samples().size());
    }

    @Test
    void leistungskurveVerdichtetAufMinutenMittelwerte() {
        // Drei 10-s-Samples in derselben Minute -> ein Kurvenpunkt mit Mittelwert.
        repo.add("2026-07-18T08:00:00Z", 3000, 300);
        repo.add("2026-07-18T08:00:10Z", 4000, 400);
        repo.add("2026-07-18T08:00:20Z", 5000, 500);
        repo.add("2026-07-18T08:01:00Z", 6000, 600);

        var curve = service.history(HistoryRange.DAY).samples();
        assertEquals(2, curve.size());
        assertEquals(Instant.parse("2026-07-18T08:00:00Z"), curve.get(0).timestamp());
        assertEquals(4000.0, curve.get(0).pvWatt(), 1e-9);
        assertEquals(400.0, curve.get(0).consumptionWatt(), 1e-9);
        assertEquals(6000.0, curve.get(1).pvWatt(), 1e-9);
    }

    @Test
    void aktuellerAngebrochenerBucketZaehltNurVerstricheneZeit() {
        // 14:00 Zurich = 12:00 UTC, jetzt 12:30 UTC -> nur 0.5 h verstrichen.
        repo.add("2026-07-18T12:05:00Z", 3000, 800);

        EnergyBucket current = bucketAt(service.history(HistoryRange.DAY), "2026-07-18T12:00:00Z");
        assertEquals(1.5, current.pvKwh(), 1e-9); // 3000 * 0.5 / 1000
        assertEquals(0.4, current.consumptionKwh(), 1e-9);
    }

    @Test
    void leereBucketsSindMitNullGefuellt() {
        EnergyHistory history = service.history(HistoryRange.DAY);
        assertTrue(history.buckets().stream().allMatch(b -> b.pvKwh() == 0 && b.consumptionKwh() == 0));
    }

    @Test
    void wochenverlaufBildetSiebenTagesBuckets() {
        EnergyHistory history = service.history(HistoryRange.WEEK);
        assertEquals(7, history.buckets().size());
    }

    @Test
    void tagesBucketMitteltUeberVolleVierundzwanzigStunden() {
        // Gestern 12:00 Zurich, ein Messpunkt PV 6000 W -> voller Tag: 6000 * 24 / 1000 = 144 kWh.
        repo.add("2026-07-17T10:00:00Z", 6000, 500);

        EnergyBucket yesterday = bucketAt(service.history(HistoryRange.WEEK), "2026-07-16T22:00:00Z");
        assertEquals(144.0, yesterday.pvKwh(), 1e-9);
    }

    @Test
    void monatsverlaufBildetDreissigTagesBuckets() {
        assertEquals(30, service.history(HistoryRange.MONTH).buckets().size());
    }

    private static EnergyBucket bucketAt(EnergyHistory history, String instant) {
        Instant target = Instant.parse(instant);
        return history.buckets().stream()
                .filter(b -> b.start().equals(target))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Kein Bucket bei " + instant));
    }

    /** In-Memory-Repository fürs Testen. */
    private static final class FakeRepo implements EnergySampleRepository {
        private final List<EnergySample> samples = new ArrayList<>();

        void add(String instant, double pv, double consumption) {
            samples.add(new EnergySample(Instant.parse(instant), pv, consumption));
        }

        @Override public void save(EnergySample sample) { samples.add(sample); }

        @Override public List<EnergySample> between(Instant from, Instant to) {
            return samples.stream()
                    .filter(s -> !s.timestamp().isBefore(from) && s.timestamp().isBefore(to))
                    .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                    .toList();
        }

        @Override public long deleteOlderThan(Instant cutoff) {
            return samples.removeIf(s -> s.timestamp().isBefore(cutoff)) ? 1 : 0;
        }

        @Override public long total() { return samples.size(); }
    }
}
