package fabianaschwanden.smarthome.domain.port.out.weather;

import fabianaschwanden.smarthome.domain.model.weather.WeatherForecast;

import java.util.Optional;

/**
 * Getriebener Port: liefert die Wettervorhersage von einer externen Quelle.
 * Adapter in {@code adapter/out/weather} implementieren diesen Port.
 */
public interface WeatherGateway {

    Optional<WeatherForecast> fetch();
}
