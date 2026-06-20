package fabianaschwanden.smarthome.adapter.in.rest.weather;

import fabianaschwanden.smarthome.adapter.in.rest.dto.weather.WeatherDto;
import fabianaschwanden.smarthome.domain.port.in.weather.CurrentWeather;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Driving Adapter — liefert die Wettervorhersage; 204, wenn die Quelle nichts hat. */
@Path("/api/weather")
@Produces(MediaType.APPLICATION_JSON)
public class WeatherResource {

    private final CurrentWeather weather;

    public WeatherResource(CurrentWeather weather) {
        this.weather = weather;
    }

    @GET
    public Response current() {
        return weather.forecast()
                .map(f -> Response.ok(WeatherDto.from(f)).build())
                .orElseGet(() -> Response.noContent().build());
    }
}
