package fabianaschwanden.smarthome.domain.model.forecast;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Der MAPE und die Frage, welche Tage ueberhaupt mitzaehlen duerfen. */
class AccuracyHistoryTest {

    private static final LocalDate TAG = LocalDate.of(2026, 8, 1);

    @Test
    void relativer_fehler_bezieht_sich_auf_den_ist_wert() {
        ForecastAccuracy tag = ForecastAccuracy.predicted(TAG, 12.0).settledWith(10.0);

        assertEquals(20.0, tag.deviationPercent().getAsDouble(), 0.001);
    }

    @Test
    void offener_tag_hat_keinen_fehler() {
        assertFalse(ForecastAccuracy.predicted(TAG, 12.0).deviationPercent().isPresent());
    }

    @Test
    void tag_ohne_ertrag_zaehlt_nicht_mit() {
        // Bei 0 kWh Ist ist der relative Fehler nicht definiert. Ihn auf 100 % zu setzen
        // wuerde einen einzigen trueben Wintertag den ganzen Durchschnitt bestimmen lassen.
        ForecastAccuracy tag = ForecastAccuracy.predicted(TAG, 5.0).settledWith(0.0);

        assertFalse(tag.deviationPercent().isPresent());
    }

    @Test
    void mape_mittelt_die_bewertbaren_tage() {
        AccuracyHistory history = AccuracyHistory.of(List.of(
                ForecastAccuracy.predicted(TAG, 12.0).settledWith(10.0),        // 20 %
                ForecastAccuracy.predicted(TAG.minusDays(1), 9.0).settledWith(10.0), // 10 %
                ForecastAccuracy.predicted(TAG.minusDays(2), 8.0)));            // offen

        assertEquals(15.0, history.mapePercent().getAsDouble(), 0.001);
        assertEquals(2, history.ratedDays());
    }

    @Test
    void ohne_bewertbaren_tag_bleibt_der_mape_leer() {
        // Leer, nicht 0: "kein Fehler messbar" und "Prognose perfekt" sind nicht dasselbe.
        AccuracyHistory history = AccuracyHistory.of(List.of(ForecastAccuracy.predicted(TAG, 8.0)));

        assertFalse(history.mapePercent().isPresent());
        assertEquals(0, history.ratedDays());
    }

    @Test
    void leere_historie_ist_zulaessig() {
        AccuracyHistory history = AccuracyHistory.of(List.of());

        assertTrue(history.days().isEmpty());
        assertFalse(history.mapePercent().isPresent());
    }

    @Test
    void negativer_ist_wert_wird_abgelehnt() {
        assertEquals(
                "actualKwh darf nicht negativ sein: -1.0",
                org.junit.jupiter.api.Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> new ForecastAccuracy(TAG, 5.0, OptionalDouble.of(-1.0))).getMessage());
    }
}
