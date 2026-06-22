package fabianaschwanden.smarthome.domain.model.weather;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class WeatherForecastTest {

    private final Instant now = Instant.parse("2026-06-22T10:00:00Z");

    @Test
    void gueltigeInstanzBautKorrekt() {
        WeatherForecast.HourEntry h = new WeatherForecast.HourEntry("11:00", 23.0, WeatherCondition.CLEAR);
        WeatherForecast f = new WeatherForecast("Zürich", 22.0, WeatherCondition.CLEAR, 25.0, 14.0,
                List.of(h), now);
        assertEquals("Zürich", f.location());
        assertEquals(22.0, f.currentTemp());
        assertEquals(WeatherCondition.CLEAR, f.condition());
        assertEquals(25.0, f.dayMax());
        assertEquals(14.0, f.dayMin());
        assertEquals(1, f.hours().size());
        assertEquals("11:00", f.hours().get(0).label());
        assertEquals(now, f.observedAt());
    }

    @Test
    void nullHoursWirdLeereListe() {
        WeatherForecast f = new WeatherForecast("Bern", 20.0, WeatherCondition.CLEAR, 22.0, 12.0, null, now);
        assertTrue(f.hours().isEmpty());
    }

    @Test
    void locationDarfNichtLeerSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeatherForecast(null, 20.0, WeatherCondition.CLEAR, 22.0, 12.0, List.of(), now));
        assertThrows(IllegalArgumentException.class,
                () -> new WeatherForecast(" ", 20.0, WeatherCondition.CLEAR, 22.0, 12.0, List.of(), now));
    }

    @Test
    void conditionDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeatherForecast("Bern", 20.0, null, 22.0, 12.0, List.of(), now));
    }

    @Test
    void observedAtDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeatherForecast("Bern", 20.0, WeatherCondition.CLEAR, 22.0, 12.0, List.of(), null));
    }

    @Test
    void hoursSindUnveraenderbar() {
        WeatherForecast f = new WeatherForecast("Bern", 20.0, WeatherCondition.CLEAR, 22.0, 12.0, List.of(), now);
        assertThrows(UnsupportedOperationException.class,
                () -> f.hours().add(new WeatherForecast.HourEntry("12:00", 21.0, WeatherCondition.CLEAR)));
    }
}
