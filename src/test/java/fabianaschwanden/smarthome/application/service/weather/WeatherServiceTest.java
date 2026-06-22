package fabianaschwanden.smarthome.application.service.weather;

import fabianaschwanden.smarthome.domain.model.weather.WeatherCondition;
import fabianaschwanden.smarthome.domain.model.weather.WeatherForecast;
import fabianaschwanden.smarthome.domain.port.out.weather.WeatherGateway;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cache-Verhalten des Wetter-Service (ohne externe Quelle). */
@QuarkusTest
class WeatherServiceTest {

    private static WeatherForecast sample() {
        return new WeatherForecast("Testort", 26, WeatherCondition.CLEAR, 28, 16,
                List.of(new WeatherForecast.HourEntry("Jetzt", 26, WeatherCondition.CLEAR)), Instant.EPOCH);
    }

    @Test
    void cacht_innerhalb_der_ttl_und_fragt_quelle_nur_einmal() {
        AtomicInteger calls = new AtomicInteger();
        WeatherGateway gateway = () -> {
            calls.incrementAndGet();
            return Optional.of(sample());
        };
        WeatherService service = new WeatherService(gateway, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        assertTrue(service.forecast().isPresent());
        service.forecast();
        service.forecast();
        assertEquals(1, calls.get(), "innerhalb der TTL darf die Quelle nur einmal gefragt werden");
    }

    @Test
    void liefert_letzten_stand_wenn_quelle_leer() {
        AtomicInteger calls = new AtomicInteger();
        WeatherGateway gateway = () -> calls.getAndIncrement() == 0 ? Optional.of(sample()) : Optional.empty();
        // Uhr, die jeden Aufruf weit nach vorne springt -> Cache läuft jedes Mal ab.
        Clock advancing = new Clock() {
            private long t = 0;
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(t += 3_600_000); }
            @Override public long millis() { return t += 3_600_000; }
        };
        WeatherService service = new WeatherService(gateway, advancing);

        assertEquals("Testort", service.forecast().orElseThrow().location());
        // Quelle jetzt leer -> letzter Stand kommt aus dem Cache.
        assertTrue(service.forecast().isPresent());
    }
}
