package fabianaschwanden.smarthome.domain.service.charging;

import fabianaschwanden.smarthome.domain.model.charging.ChargingSession;
import fabianaschwanden.smarthome.domain.model.energy.EnergySample;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Schaetzung der Ladeenergie aus dem Verbrauchssprung. */
class ChargingEnergyEstimatorTest {

    private static final Instant EIN = Instant.parse("2026-08-16T10:00:00Z");
    private static final Duration EINSCHWINGEN = Duration.ofSeconds(60);
    private static final Duration VERGLEICHSFENSTER = Duration.ofMinutes(2);

    private final ChargingEnergyEstimator estimator = new ChargingEnergyEstimator();

    /** Messpunkte alle 10 s von {@code von} bis {@code bis} mit dem gegebenen Verbrauch. */
    private static List<EnergySample> reihe(Instant von, Instant bis, double verbrauchWatt) {
        List<EnergySample> samples = new ArrayList<>();
        for (Instant t = von; t.isBefore(bis); t = t.plusSeconds(10)) {
            samples.add(new EnergySample(t, 0, verbrauchWatt));
        }
        return samples;
    }

    private Optional<ChargingSession> schaetze(List<EnergySample> samples, Instant aus) {
        return estimator.estimate(samples, EIN, aus, EINSCHWINGEN, VERGLEICHSFENSTER);
    }

    @Test
    void nimmt_den_sprung_als_ladeleistung() {
        Instant aus = EIN.plus(Duration.ofHours(2));
        List<EnergySample> samples = new ArrayList<>();
        samples.addAll(reihe(EIN.minus(VERGLEICHSFENSTER), EIN, 400));   // vorher 400 W
        samples.addAll(reihe(EIN, aus, 2400));                            // waehrend 2400 W

        ChargingSession session = schaetze(samples, aus).orElseThrow();

        assertEquals(2000.0, session.watt());          // Sprung: 2400 - 400
        assertEquals(4.0, session.energyKwh());        // 2000 W ueber 2 h
    }

    @Test
    void uebergeht_die_einschwingzeit() {
        // Ein Ladegeraet faehrt seine Leistung nicht schlagartig hoch; die ersten
        // Sekunden waeren zu niedrig und wuerden die Schaetzung druecken.
        Instant aus = EIN.plus(Duration.ofHours(1));
        List<EnergySample> samples = new ArrayList<>();
        samples.addAll(reihe(EIN.minus(VERGLEICHSFENSTER), EIN, 400));
        samples.addAll(reihe(EIN, EIN.plusSeconds(60), 800));            // Anlauf
        samples.addAll(reihe(EIN.plusSeconds(60), aus, 2400));           // volle Leistung

        assertEquals(2000.0, schaetze(samples, aus).orElseThrow().watt());
    }

    @Test
    void laesst_sich_von_einem_ausreisser_nicht_verschieben() {
        // Der Backofen, der einmal kurz anspringt: Der Median haelt dagegen, ein
        // Mittelwert wuerde mitwandern.
        Instant aus = EIN.plus(Duration.ofHours(1));
        List<EnergySample> samples = new ArrayList<>();
        samples.addAll(reihe(EIN.minus(VERGLEICHSFENSTER), EIN, 400));
        samples.addAll(reihe(EIN.plusSeconds(60), aus, 2400));
        samples.add(new EnergySample(EIN.plusSeconds(300), 0, 9000));    // Ausreisser

        assertEquals(2000.0, schaetze(samples, aus).orElseThrow().watt());
    }

    @Test
    void meldet_keine_negative_leistung() {
        // Faellt der Verbrauch beim Einschalten, hat eine andere Last aufgehoert - ueber
        // das Ladegeraet sagt das nichts.
        Instant aus = EIN.plus(Duration.ofHours(1));
        List<EnergySample> samples = new ArrayList<>();
        samples.addAll(reihe(EIN.minus(VERGLEICHSFENSTER), EIN, 3000));
        samples.addAll(reihe(EIN.plusSeconds(60), aus, 500));

        ChargingSession session = schaetze(samples, aus).orElseThrow();

        assertEquals(0.0, session.watt());
        assertEquals(0.0, session.energyKwh());
    }

    @Test
    void schaetzt_ohne_vergleichswerte_gar_nicht() {
        // Eine 0 auszuweisen waere schlimmer als nichts: Sie saehe aus wie "nicht geladen".
        Instant aus = EIN.plus(Duration.ofHours(1));

        assertTrue(schaetze(reihe(EIN.plusSeconds(60), aus, 2400), aus).isEmpty());
    }

    @Test
    void schaetzt_ohne_messpunkte_waehrend_des_ladens_gar_nicht() {
        Instant aus = EIN.plus(Duration.ofHours(1));

        assertTrue(schaetze(reihe(EIN.minus(VERGLEICHSFENSTER), EIN, 400), aus).isEmpty());
    }

    @Test
    void haelt_einen_sehr_kurzen_ladevorgang_aus() {
        // Kuerzer als die Einschwingzeit: Es gibt keine belastbaren Werte "waehrend".
        Instant aus = EIN.plusSeconds(30);
        List<EnergySample> samples = reihe(EIN.minus(VERGLEICHSFENSTER), aus, 400);

        assertTrue(schaetze(samples, aus).isEmpty());
    }
}
