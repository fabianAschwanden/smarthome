package fabianaschwanden.smarthome.domain.service.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.IrradiancePoint;
import fabianaschwanden.smarthome.domain.model.forecast.PlantProfile;
import fabianaschwanden.smarthome.domain.model.forecast.PvForecast;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Rechnet aus einer Strahlungsprognose und dem gelernten Anlagenprofil die erwartete
 * PV-Leistung je Stunde. Reiner Domain-Service: zustandslos, framework-frei; Zeitzone und
 * Rechenzeitpunkt kommen als Parameter herein, damit die Berechnung deterministisch
 * testbar bleibt.
 */
public final class PvForecaster {

    /** Eine Stunde Leistung in Watt ergibt Wattstunden; /1000 sind es Kilowattstunden. */
    private static final double WH_TO_KWH = 1000.0;

    /**
     * Erwartete Leistung je Stunde plus Tagessummen für heute und morgen.
     *
     * @param zone      Zeitzone, in der Stunden-Slots und Tagesgrenzen gelten
     * @param computedAt Rechenzeitpunkt; bestimmt zugleich, welcher Tag „heute" ist
     */
    public PvForecast forecast(
            PlantProfile profile, List<IrradiancePoint> series, ZoneId zone, Instant computedAt) {
        if (profile == null) {
            throw new IllegalArgumentException("profile darf nicht null sein");
        }
        if (zone == null || computedAt == null) {
            throw new IllegalArgumentException("zone und computedAt dürfen nicht null sein");
        }
        List<IrradiancePoint> points = series == null ? List.of() : series;

        LocalDate today = LocalDate.ofInstant(computedAt, zone);
        LocalDate tomorrow = today.plusDays(1);
        double todayWh = 0;
        double tomorrowWh = 0;
        List<PvForecast.HourEntry> hours = new ArrayList<>(points.size());

        for (IrradiancePoint point : points) {
            int hourOfDay = point.hour().atZone(zone).getHour();
            double watt = profile.factorAt(hourOfDay) * point.gtiWattPerSqm();
            // Deckel auf das historische Maximum: eine GTI-Spitze in der Prognose darf
            // keine Leistung ergeben, die die Anlage physisch nie erreicht hat.
            if (profile.maxObservedPvWatt() > 0) {
                watt = Math.min(watt, profile.maxObservedPvWatt());
            }
            hours.add(new PvForecast.HourEntry(point.hour(), watt, point.gtiWattPerSqm()));

            LocalDate day = LocalDate.ofInstant(point.hour(), zone);
            if (day.equals(today)) {
                todayWh += watt;
            } else if (day.equals(tomorrow)) {
                tomorrowWh += watt;
            }
        }

        return new PvForecast(
                hours,
                todayWh / WH_TO_KWH,
                tomorrowWh / WH_TO_KWH,
                profile.confidence(),
                profile.learnedAt(),
                computedAt);
    }
}
