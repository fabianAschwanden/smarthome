package fabianaschwanden.smarthome.adapter.out.irradiance.mock;

import fabianaschwanden.smarthome.domain.model.forecast.IrradiancePoint;
import fabianaschwanden.smarthome.domain.model.forecast.IrradianceSeries;
import fabianaschwanden.smarthome.domain.port.out.forecast.IrradianceGateway;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Synthetische Strahlungsreihe für Entwicklung und Test – eine Glockenkurve über den Tag,
 * wie an einem wolkenlosen Sommertag. Damit funktionieren Dashboard und Tests ohne
 * Internet (SPEC §5).
 *
 * <p>Bewusst deterministisch: Die Form hängt nur an der Stunde des Tages, nicht an
 * Zufall. Zwei Abrufe zur selben Stunde liefern dieselben Werte, sonst wären Tests
 * darauf nicht stabil.
 */
@ApplicationScoped
@UnlessBuildProperty(name = "smarthome.real-devices", stringValue = "true", enableIfMissing = true)
public class MockIrradianceGateway implements IrradianceGateway {

    /** Scheitel der Kurve in W/m² – realistischer Sommerwert für die Modulebene. */
    static final double PEAK_WATT_PER_SQM = 850.0;

    /** Wann die Sonne am höchsten steht (lokale Stunde). */
    static final double PEAK_HOUR = 13.0;

    /** Breite der Glocke; ~3 h Standardabweichung. */
    static final double WIDTH_HOURS = 3.0;

    /**
     * Unterhalb dieses Werts gilt es als Nacht und wird hart auf 0 gesetzt. Die Glocke
     * laeuft mathematisch nie ganz aus – ohne Schnitt stuenden um 3 Uhr noch gut 3 W/m².
     * Mit 10 W/m² scheint die Sonne von etwa 5 bis 21 Uhr, der Rest ist echte Null.
     */
    private static final double NIGHT_CUTOFF = 10.0;

    private static final int PAST_DAYS = 7;
    private static final int FORECAST_DAYS = 2;

    private final ZoneId zone = ZoneId.systemDefault();

    @Override
    public Optional<IrradianceSeries> fetch() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
        LocalDate firstDay = LocalDate.ofInstant(now, zone).minusDays(PAST_DAYS);
        int totalHours = (PAST_DAYS + FORECAST_DAYS) * 24;

        List<IrradiancePoint> past = new ArrayList<>();
        List<IrradiancePoint> forecast = new ArrayList<>();
        Instant start = firstDay.atStartOfDay(zone).toInstant();
        for (int i = 0; i < totalHours; i++) {
            Instant hour = start.plus(i, ChronoUnit.HOURS);
            IrradiancePoint point = new IrradiancePoint(hour, gtiAt(hour.atZone(zone).getHour()));
            if (hour.isBefore(now)) {
                past.add(point);
            } else {
                forecast.add(point);
            }
        }
        return Optional.of(new IrradianceSeries(past, forecast, now));
    }

    /** Glockenkurve um {@link #PEAK_HOUR}; nachts exakt 0. */
    static double gtiAt(int hourOfDay) {
        double offset = hourOfDay - PEAK_HOUR;
        double value = PEAK_WATT_PER_SQM * Math.exp(-(offset * offset) / (2 * WIDTH_HOURS * WIDTH_HOURS));
        return value < NIGHT_CUTOFF ? 0.0 : value;
    }
}
