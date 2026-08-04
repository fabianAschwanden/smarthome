package fabianaschwanden.smarthome.domain.service.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.Confidence;
import fabianaschwanden.smarthome.domain.model.forecast.IrradiancePoint;
import fabianaschwanden.smarthome.domain.model.forecast.PlantProfile;
import fabianaschwanden.smarthome.domain.model.forecast.PvForecast;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PvForecasterTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    private final PvForecaster forecaster = new PvForecaster();
    private final Instant learnedAt = Instant.parse("2026-08-04T03:00:00Z");
    /** 4. August 2026, 06:00 lokal (Sommerzeit: UTC+2). */
    private final Instant computedAt = Instant.parse("2026-08-04T04:00:00Z");

    private PlantProfile profileWithFactor(double factor, double maxWatt) {
        return new PlantProfile(
                Collections.nCopies(PlantProfile.SLOTS, factor), maxWatt, learnedAt, Confidence.LEARNED);
    }

    /** Stundenpunkte ab einem lokalen Zeitpunkt mit konstanter Strahlung. */
    private List<IrradiancePoint> series(String startUtc, int hours, double gti) {
        List<IrradiancePoint> points = new ArrayList<>();
        Instant start = Instant.parse(startUtc);
        for (int i = 0; i < hours; i++) {
            points.add(new IrradiancePoint(start.plusSeconds(i * 3600L), gti));
        }
        return points;
    }

    @Test
    void rechnetLeistungAusFaktorUndStrahlung() {
        PvForecast forecast = forecaster.forecast(
                profileWithFactor(5.0, 0), series("2026-08-04T09:00:00Z", 3, 600), ZURICH, computedAt);

        assertEquals(3, forecast.hours().size());
        assertEquals(3000.0, forecast.hours().get(0).expectedPvWatt(), 0.0001);
        assertEquals(600.0, forecast.hours().get(0).gtiWattPerSqm(), 0.0001);
    }

    @Test
    void deckeltAufHistorischesMaximum() {
        // Faktor x GTI ergaebe 6000 W, die Anlage hat aber nie mehr als 4200 W geliefert.
        PvForecast forecast = forecaster.forecast(
                profileWithFactor(10.0, 4200), series("2026-08-04T09:00:00Z", 1, 600), ZURICH, computedAt);

        assertEquals(4200.0, forecast.hours().get(0).expectedPvWatt(), 0.0001);
    }

    @Test
    void ohneBekanntesMaximumWirdNichtGedeckelt() {
        PvForecast forecast = forecaster.forecast(
                profileWithFactor(10.0, 0), series("2026-08-04T09:00:00Z", 1, 600), ZURICH, computedAt);

        assertEquals(6000.0, forecast.hours().get(0).expectedPvWatt(), 0.0001);
    }

    @Test
    void summiertHeuteUndMorgenGetrennt() {
        // 09:00Z = 11:00 lokal am 4.8., dann 24 Stunden -> reicht in den 5.8. hinein.
        List<IrradiancePoint> points = series("2026-08-04T09:00:00Z", 24, 100);

        PvForecast forecast = forecaster.forecast(profileWithFactor(10.0, 0), points, ZURICH, computedAt);

        // 1000 W je Stunde. Lokal 11:00-23:59 am 4.8. sind 13 Stunden, der Rest faellt auf den 5.8.
        assertEquals(13.0, forecast.todayKwh(), 0.0001);
        assertEquals(11.0, forecast.tomorrowKwh(), 0.0001);
    }

    @Test
    void uebernimmtConfidenceUndLernzeitpunktAusDemProfil() {
        PlantProfile rough = new PlantProfile(
                Collections.nCopies(PlantProfile.SLOTS, 1.0), 0, learnedAt, Confidence.ROUGH);

        PvForecast forecast = forecaster.forecast(rough, series("2026-08-04T09:00:00Z", 1, 10), ZURICH, computedAt);

        assertEquals(Confidence.ROUGH, forecast.confidence());
        assertEquals(learnedAt, forecast.learnedAt());
        assertEquals(computedAt, forecast.computedAt());
    }

    @Test
    void leereReiheErgibtLeereProgose() {
        PvForecast forecast = forecaster.forecast(profileWithFactor(5.0, 0), List.of(), ZURICH, computedAt);

        assertTrue(forecast.hours().isEmpty());
        assertEquals(0.0, forecast.todayKwh(), 0.0001);
        assertEquals(0.0, forecast.tomorrowKwh(), 0.0001);
    }

    @Test
    void nullProfilWirdAbgewiesen() {
        assertThrows(IllegalArgumentException.class,
                () -> forecaster.forecast(null, List.of(), ZURICH, computedAt));
        assertThrows(IllegalArgumentException.class,
                () -> forecaster.forecast(profileWithFactor(1, 0), List.of(), null, computedAt));
    }
}
