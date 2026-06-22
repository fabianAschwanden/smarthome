package fabianaschwanden.smarthome.adapter.out.weather.openmeteo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fabianaschwanden.smarthome.adapter.out.weather.WeatherConfig;
import fabianaschwanden.smarthome.domain.model.weather.WeatherCondition;
import fabianaschwanden.smarthome.domain.model.weather.WeatherForecast;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testet das Open-Meteo-Mapping. Der Endpoint ist im Adapter fest verdrahtet
 * (kein konfigurierbarer Base-URL), daher wird {@code fetch()} nicht gegen einen
 * Fake-Server gefahren – das würde entweder das echte Internet ansprechen
 * (nicht-deterministisch) oder nichts testen. Stattdessen werden die beiden
 * tragenden privaten Methoden ({@code condition} = WMO-Mapping und {@code hours}
 * = Stundenauswahl/-parsing) reflektiv aufgerufen.
 *
 * <p>{@code @QuarkusTest}, damit die Coverage ins Quarkus-JaCoCo zählt.
 */
@QuarkusTest
class OpenMeteoWeatherGatewayTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenMeteoWeatherGateway gateway() {
        WeatherConfig config = new WeatherConfig() {
            @Override public String location() { return "Testort"; }
            @Override public double latitude() { return 46.95; }
            @Override public double longitude() { return 7.45; }
        };
        return new OpenMeteoWeatherGateway(config, MAPPER);
    }

    private static WeatherCondition condition(int code) throws Exception {
        Method m = OpenMeteoWeatherGateway.class.getDeclaredMethod("condition", int.class);
        m.setAccessible(true);
        return (WeatherCondition) m.invoke(null, code);
    }

    @SuppressWarnings("unchecked")
    private List<WeatherForecast.HourEntry> hours(JsonNode hourly) throws Exception {
        Method m = OpenMeteoWeatherGateway.class.getDeclaredMethod("hours", JsonNode.class);
        m.setAccessible(true);
        return (List<WeatherForecast.HourEntry>) m.invoke(gateway(), hourly);
    }

    @Test
    void mapptWmoCodesAufZustaende() throws Exception {
        assertEquals(WeatherCondition.CLEAR, condition(0));
        assertEquals(WeatherCondition.MAINLY_CLEAR, condition(1));
        assertEquals(WeatherCondition.PARTLY_CLOUDY, condition(2));
        assertEquals(WeatherCondition.CLOUDY, condition(3));
        assertEquals(WeatherCondition.FOG, condition(45));
        assertEquals(WeatherCondition.FOG, condition(48));
        assertEquals(WeatherCondition.DRIZZLE, condition(51));
        assertEquals(WeatherCondition.DRIZZLE, condition(57));
        assertEquals(WeatherCondition.RAIN, condition(61));
        assertEquals(WeatherCondition.RAIN, condition(67));
        assertEquals(WeatherCondition.SNOW, condition(75));
        assertEquals(WeatherCondition.SHOWERS, condition(80));
        assertEquals(WeatherCondition.SHOWERS, condition(86));
        assertEquals(WeatherCondition.THUNDERSTORM, condition(95));
        assertEquals(WeatherCondition.THUNDERSTORM, condition(99));
    }

    @Test
    void unbekannterCodeIstUnknown() throws Exception {
        assertEquals(WeatherCondition.UNKNOWN, condition(-1));
        assertEquals(WeatherCondition.UNKNOWN, condition(123));
    }

    @Test
    void hoursWaehltAbLaufenderStundeUndBeschriftet() throws Exception {
        // Tagesreihe 00..23 Uhr bauen, damit die laufende Stunde garantiert enthalten ist.
        LocalDateTime base = LocalDateTime.now().toLocalDate().atStartOfDay();
        DateTimeFormatter iso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        StringBuilder times = new StringBuilder();
        StringBuilder temps = new StringBuilder();
        StringBuilder codes = new StringBuilder();
        for (int h = 0; h < 24; h++) {
            if (h > 0) { times.append(','); temps.append(','); codes.append(','); }
            times.append('"').append(base.plusHours(h).format(iso)).append('"');
            temps.append(h);          // Temperatur = Stundenindex (eindeutig)
            codes.append(0);          // alle CLEAR
        }
        String json = "{\"time\":[" + times + "],\"temperature_2m\":[" + temps
                + "],\"weather_code\":[" + codes + "]}";

        List<WeatherForecast.HourEntry> result = hours(MAPPER.readTree(json));

        int nowHour = LocalDateTime.now().getHour();
        int expected = Math.min(6, 24 - nowHour);
        assertEquals(expected, result.size());
        assertEquals("Jetzt", result.get(0).label());
        assertEquals((double) nowHour, result.get(0).temp());
        assertEquals(WeatherCondition.CLEAR, result.get(0).condition());
        if (result.size() > 1) {
            assertTrue(result.get(1).label().endsWith("Uhr"));
            assertFalse(result.get(1).label().equals("Jetzt"));
        }
    }

    @Test
    void hoursOhneTimeArrayIstLeer() throws Exception {
        List<WeatherForecast.HourEntry> result = hours(MAPPER.readTree("{}"));
        assertTrue(result.isEmpty());
    }

    @Test
    void fetchInstanziierbar() {
        // Reiner Konstruktions-Smoke-Test, kein Netzwerkaufruf.
        OpenMeteoWeatherGateway g = gateway();
        org.junit.jupiter.api.Assertions.assertNotNull(g);
    }
}
