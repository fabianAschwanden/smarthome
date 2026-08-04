package fabianaschwanden.smarthome.adapter.out.irradiance.openmeteo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fabianaschwanden.smarthome.adapter.out.irradiance.IrradianceConfig;
import fabianaschwanden.smarthome.domain.model.forecast.IrradiancePoint;
import fabianaschwanden.smarthome.domain.model.forecast.IrradianceSeries;
import fabianaschwanden.smarthome.domain.port.out.forecast.IrradianceGateway;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Holt die Strahlung in Modulebene von Open-Meteo (kein API-Key), in einem Abruf für
 * Vergangenheit und Vorhersage (SPEC §2.1).
 *
 * <p>{@code global_tilted_irradiance} ist bereits auf die Modulebene gerechnet –
 * Neigung und Ausrichtung gehen als Query-Parameter mit, die Umrechnung übernimmt
 * Open-Meteo. Dass der Dienst diese Grösse auch für {@code past_days} liefert, ist
 * geprüft (SPEC §8); daran hängt das Lernen des Anlagenprofils.
 *
 * <p>Aktiv im Echtbetrieb; im Mock/Test liefert {@code MockIrradianceGateway} eine
 * synthetische Kurve.
 */
@ApplicationScoped
@IfBuildProperty(name = "smarthome.real-devices", stringValue = "true")
public class OpenMeteoIrradianceGateway implements IrradianceGateway {

    private static final Logger LOG = Logger.getLogger(OpenMeteoIrradianceGateway.class);

    /** So viele Tage Vergangenheit deckt das Lernfenster ab (SPEC §2.1). */
    private static final int PAST_DAYS = 7;

    /** Heute und morgen – weiter reicht die Empfehlung nicht. */
    private static final int FORECAST_DAYS = 2;

    private final IrradianceConfig plant;
    private final double latitude;
    private final double longitude;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public OpenMeteoIrradianceGateway(
            IrradianceConfig plant,
            // Standort als rohe Schluessel statt ueber WeatherConfig: Adapter duerfen
            // einander nicht referenzieren (Blueprint §3.4, ArchUnit prueft das).
            @ConfigProperty(name = "weather.latitude") double latitude,
            @ConfigProperty(name = "weather.longitude") double longitude,
            ObjectMapper mapper,
            @ConfigProperty(
                            name = "forecast.irradiance.base-url",
                            defaultValue = "https://api.open-meteo.com/v1/forecast")
                    String baseUrl) {
        this.plant = plant;
        this.latitude = latitude;
        this.longitude = longitude;
        this.mapper = mapper;
        this.baseUrl = baseUrl;
    }

    @Override
    public Optional<IrradianceSeries> fetch() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url()))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.warnf("Open-Meteo (Strahlung) HTTP %d", response.statusCode());
                return Optional.empty();
            }
            return Optional.of(parse(mapper.readTree(response.body()), Instant.now()));
        } catch (Exception e) {
            LOG.warnf("Strahlungsreihe nicht lesbar: %s", e.getMessage());
            return Optional.empty();
        }
    }

    private String url() {
        return baseUrl
                + "?latitude=" + latitude
                + "&longitude=" + longitude
                + "&hourly=global_tilted_irradiance"
                + "&tilt=" + plant.tilt()
                + "&azimuth=" + plant.azimuth()
                + "&past_days=" + PAST_DAYS
                + "&forecast_days=" + FORECAST_DAYS
                + "&timezone=Europe%2FZurich";
    }

    /**
     * Zerlegt den Hourly-Block in Vergangenheit und Vorhersage.
     *
     * <p>Die Zeitstempel sind lokale Zeit ohne Offset – die Zone steht separat im
     * Response und wird von dort gelesen. Ein fester Offset wäre über einen
     * Zeitumstellungs-Wechsel hinweg falsch, und das Lernfenster von sieben Tagen kann
     * genau darüber liegen.
     */
    IrradianceSeries parse(JsonNode root, Instant now) {
        JsonNode hourly = root.path("hourly");
        JsonNode times = hourly.path("time");
        JsonNode values = hourly.path("global_tilted_irradiance");
        if (!times.isArray() || !values.isArray()) {
            LOG.warn("Strahlungsreihe ohne hourly-Block");
            return new IrradianceSeries(List.of(), List.of(), now);
        }
        ZoneId zone = zoneOf(root);

        List<IrradiancePoint> past = new ArrayList<>();
        List<IrradiancePoint> forecast = new ArrayList<>();
        for (int i = 0; i < times.size() && i < values.size(); i++) {
            JsonNode value = values.get(i);
            // Open-Meteo liefert für einzelne Stunden null statt einer Zahl; die
            // ueberspringen wir, statt sie als 0 zu lesen - 0 hiesse "keine Sonne".
            if (value == null || value.isNull()) {
                continue;
            }
            Instant hour = LocalDateTime.parse(times.get(i).asText()).atZone(zone).toInstant();
            IrradiancePoint point = new IrradiancePoint(hour, Math.max(0, value.asDouble()));
            if (hour.isBefore(now)) {
                past.add(point);
            } else {
                forecast.add(point);
            }
        }
        return new IrradianceSeries(past, forecast, now);
    }

    /** Zone aus dem Response; fällt auf die System-Zone zurück, wenn das Feld fehlt. */
    private static ZoneId zoneOf(JsonNode root) {
        String timezone = root.path("timezone").asText(null);
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            LOG.warnf("Unbekannte Zeitzone '%s', nutze System-Zone", timezone);
            return ZoneId.systemDefault();
        }
    }
}
