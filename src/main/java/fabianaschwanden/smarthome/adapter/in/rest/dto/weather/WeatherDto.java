package fabianaschwanden.smarthome.adapter.in.rest.dto.weather;

import fabianaschwanden.smarthome.domain.model.weather.WeatherForecast;

import java.util.List;

/** REST-DTO der Wettervorhersage (publizierte Sprache der REST-Schicht). */
public record WeatherDto(
        String location,
        double currentTemp,
        String condition,
        double dayMax,
        double dayMin,
        List<HourDto> hours,
        String observedAt) {

    public record HourDto(String label, double temp, String condition) {
    }

    public static WeatherDto from(WeatherForecast f) {
        List<HourDto> hours = f.hours().stream()
                .map(h -> new HourDto(h.label(), h.temp(), h.condition().name()))
                .toList();
        return new WeatherDto(
                f.location(),
                f.currentTemp(),
                f.condition().name(),
                f.dayMax(),
                f.dayMin(),
                hours,
                f.observedAt().toString());
    }
}
