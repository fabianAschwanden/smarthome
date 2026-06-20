package fabianaschwanden.smarthome.adapter.out.weather.mock;

import fabianaschwanden.smarthome.adapter.out.weather.WeatherConfig;
import fabianaschwanden.smarthome.domain.model.weather.WeatherCondition;
import fabianaschwanden.smarthome.domain.model.weather.WeatherForecast;
import fabianaschwanden.smarthome.domain.port.out.weather.WeatherGateway;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Synthetische Wetterquelle für Entwicklung/Test (aktiv, solange nicht gegen echte
 * Geräte gefahren wird). Liefert feste, plausible Werte für den konfigurierten Ort.
 */
@ApplicationScoped
@UnlessBuildProperty(name = "smarthome.real-devices", stringValue = "true", enableIfMissing = true)
public class MockWeatherGateway implements WeatherGateway {

    private final WeatherConfig config;

    public MockWeatherGateway(WeatherConfig config) {
        this.config = config;
    }

    @Override
    public Optional<WeatherForecast> fetch() {
        return Optional.of(new WeatherForecast(
                config.location(),
                26,
                WeatherCondition.MAINLY_CLEAR,
                28,
                16,
                List.of(
                        new WeatherForecast.HourEntry("Jetzt", 26, WeatherCondition.MAINLY_CLEAR),
                        new WeatherForecast.HourEntry("13 Uhr", 27, WeatherCondition.CLEAR),
                        new WeatherForecast.HourEntry("14 Uhr", 27, WeatherCondition.CLEAR),
                        new WeatherForecast.HourEntry("15 Uhr", 28, WeatherCondition.PARTLY_CLOUDY),
                        new WeatherForecast.HourEntry("16 Uhr", 28, WeatherCondition.CLEAR),
                        new WeatherForecast.HourEntry("17 Uhr", 27, WeatherCondition.PARTLY_CLOUDY)),
                Instant.now()));
    }
}
