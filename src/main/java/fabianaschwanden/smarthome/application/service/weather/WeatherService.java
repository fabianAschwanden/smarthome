package fabianaschwanden.smarthome.application.service.weather;

import fabianaschwanden.smarthome.domain.model.weather.WeatherForecast;
import fabianaschwanden.smarthome.domain.port.in.weather.CurrentWeather;
import fabianaschwanden.smarthome.domain.port.out.weather.WeatherGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Application-Service: liefert die Wettervorhersage und cacht sie kurz. Das Wetter
 * ändert sich langsam, das UI pollt aber häufig – darum wird die externe Quelle
 * höchstens alle {@link #TTL} angefragt; der letzte Stand wird sonst weitergegeben.
 */
@ApplicationScoped
public class WeatherService implements CurrentWeather {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final WeatherGateway gateway;
    private final Clock clock;

    private volatile WeatherForecast cached;
    private volatile long cachedAtMillis;

    @Inject
    public WeatherService(WeatherGateway gateway) {
        this(gateway, Clock.systemUTC());
    }

    // Sichtbar fürs Testen.
    WeatherService(WeatherGateway gateway, Clock clock) {
        this.gateway = gateway;
        this.clock = clock;
    }

    @Override
    public Optional<WeatherForecast> forecast() {
        WeatherForecast current = cached;
        if (current != null && clock.millis() - cachedAtMillis < TTL.toMillis()) {
            return Optional.of(current);
        }
        Optional<WeatherForecast> fresh = gateway.fetch();
        if (fresh.isPresent()) {
            cached = fresh.get();
            cachedAtMillis = clock.millis();
            return fresh;
        }
        // Quelle gerade nicht erreichbar: letzten bekannten Stand weitergeben.
        return Optional.ofNullable(current);
    }
}
