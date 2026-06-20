package fabianaschwanden.smarthome.adapter.out.weather.openmeteo;

import fabianaschwanden.smarthome.adapter.out.weather.WeatherConfig;
import fabianaschwanden.smarthome.domain.model.weather.WeatherCondition;
import fabianaschwanden.smarthome.domain.model.weather.WeatherForecast;
import fabianaschwanden.smarthome.domain.port.out.weather.WeatherGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Holt die Vorhersage von Open-Meteo (kein API-Key). Endpoint:
 * {@code GET api.open-meteo.com/v1/forecast} mit current/hourly/daily.
 * Aktiv im Echtbetrieb ({@code smarthome.real-devices=true}); im Mock/Test liefert
 * {@code MockWeatherGateway} feste Werte.
 */
@ApplicationScoped
@IfBuildProperty(name = "smarthome.real-devices", stringValue = "true")
public class OpenMeteoWeatherGateway implements WeatherGateway {

    private static final Logger LOG = Logger.getLogger(OpenMeteoWeatherGateway.class);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH");

    private final WeatherConfig config;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public OpenMeteoWeatherGateway(WeatherConfig config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
    }

    @Override
    public Optional<WeatherForecast> fetch() {
        try {
            String url = "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + config.latitude()
                    + "&longitude=" + config.longitude()
                    + "&current=temperature_2m,weather_code"
                    + "&hourly=temperature_2m,weather_code"
                    + "&daily=temperature_2m_max,temperature_2m_min"
                    + "&timezone=Europe%2FZurich&forecast_days=1";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.warnf("Open-Meteo HTTP %d", response.statusCode());
                return Optional.empty();
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode current = root.path("current");
            JsonNode daily = root.path("daily");
            return Optional.of(new WeatherForecast(
                    config.location(),
                    current.path("temperature_2m").asDouble(),
                    condition(current.path("weather_code").asInt(-1)),
                    daily.path("temperature_2m_max").path(0).asDouble(),
                    daily.path("temperature_2m_min").path(0).asDouble(),
                    hours(root.path("hourly")),
                    Instant.now()));
        } catch (Exception e) {
            LOG.warnf("Open-Meteo nicht lesbar: %s", e.getMessage());
            return Optional.empty();
        }
    }

    /** Wählt die nächsten 6 Stunden ab der laufenden Stunde aus dem hourly-Block. */
    private List<WeatherForecast.HourEntry> hours(JsonNode hourly) {
        JsonNode times = hourly.path("time");
        JsonNode temps = hourly.path("temperature_2m");
        JsonNode codes = hourly.path("weather_code");
        if (!times.isArray()) {
            return List.of();
        }
        int start = 0;
        int nowHour = LocalDateTime.now().getHour();
        for (int i = 0; i < times.size(); i++) {
            LocalDateTime t = LocalDateTime.parse(times.get(i).asText());
            if (t.getHour() == nowHour) {
                start = i;
                break;
            }
        }
        List<WeatherForecast.HourEntry> result = new ArrayList<>();
        for (int i = start; i < Math.min(start + 6, times.size()); i++) {
            LocalDateTime t = LocalDateTime.parse(times.get(i).asText());
            String label = i == start ? "Jetzt" : t.format(HOUR) + " Uhr";
            result.add(new WeatherForecast.HourEntry(
                    label, temps.get(i).asDouble(), condition(codes.get(i).asInt(-1))));
        }
        return result;
    }

    /** WMO-Wettercode -> fachlicher Zustand (siehe Open-Meteo-Doku). */
    private static WeatherCondition condition(int code) {
        return switch (code) {
            case 0 -> WeatherCondition.CLEAR;
            case 1 -> WeatherCondition.MAINLY_CLEAR;
            case 2 -> WeatherCondition.PARTLY_CLOUDY;
            case 3 -> WeatherCondition.CLOUDY;
            case 45, 48 -> WeatherCondition.FOG;
            case 51, 53, 55, 56, 57 -> WeatherCondition.DRIZZLE;
            case 61, 63, 65, 66, 67 -> WeatherCondition.RAIN;
            case 71, 73, 75, 77 -> WeatherCondition.SNOW;
            case 80, 81, 82, 85, 86 -> WeatherCondition.SHOWERS;
            case 95, 96, 99 -> WeatherCondition.THUNDERSTORM;
            default -> WeatherCondition.UNKNOWN;
        };
    }
}
