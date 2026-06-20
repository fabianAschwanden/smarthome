package fabianaschwanden.smarthome.domain.port.in.weather;

import fabianaschwanden.smarthome.domain.model.weather.WeatherForecast;

import java.util.Optional;

/** Treiber-Port: aktuelle Wettervorhersage für den konfigurierten Ort. */
public interface CurrentWeather {

    /** Liefert die Vorhersage; {@code empty}, wenn die Quelle nicht erreichbar ist. */
    Optional<WeatherForecast> forecast();
}
