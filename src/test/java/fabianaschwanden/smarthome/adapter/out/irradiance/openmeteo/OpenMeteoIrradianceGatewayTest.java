package fabianaschwanden.smarthome.adapter.out.irradiance.openmeteo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import fabianaschwanden.smarthome.adapter.out.irradiance.IrradianceConfig;
import fabianaschwanden.smarthome.domain.model.forecast.IrradianceSeries;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testet den Strahlungs-Adapter gegen einen lokalen Fake-HTTP-Server (kein echtes
 * Open-Meteo). Der Adapter wird direkt instanziiert – die {@code @IfBuildProperty}-
 * Aktivierung ist eine Build-Zeit-Frage und für den Parsing-Test nicht nötig.
 *
 * <p>Die Zeitstempel im Fake-JSON werden relativ zur echten Uhr erzeugt: nur so lässt
 * sich die Aufteilung in Vergangenheit und Vorhersage prüfen, die der Adapter anhand
 * von „jetzt" trifft.
 *
 * <p>{@code @QuarkusTest}, damit die Coverage ins Quarkus-JaCoCo zählt.
 */
@QuarkusTest
class OpenMeteoIrradianceGatewayTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    private HttpServer server;
    private String baseUrl;
    private volatile String body = "";
    private volatile int status = 200;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/forecast", exchange -> {
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/forecast";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private OpenMeteoIrradianceGateway gateway() {
        IrradianceConfig plant = new IrradianceConfig() {
            @Override public double tilt() { return 30; }
            @Override public double azimuth() { return 0; }
            @Override public double kwp() { return 10; }
        };
        return new OpenMeteoIrradianceGateway(plant, 46.875, 8.607, MAPPER, baseUrl);
    }

    /** Stundenreihe um „jetzt" herum: {@code before} Stunden davor, {@code after} danach. */
    private String jsonAround(int before, int after, double value) {
        Instant base = Instant.now().truncatedTo(ChronoUnit.HOURS);
        List<String> times = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (int i = -before; i < after; i++) {
            LocalDateTime local = LocalDateTime.ofInstant(base.plus(i, ChronoUnit.HOURS), ZURICH);
            times.add("\"" + local.toString().substring(0, 16) + "\"");
            values.add(String.valueOf(value));
        }
        return """
                {"timezone":"Europe/Zurich","hourly":{"time":[%s],"global_tilted_irradiance":[%s]}}"""
                .formatted(String.join(",", times), String.join(",", values));
    }

    @Test
    void trenntVergangenheitVonVorhersage() {
        body = jsonAround(3, 4, 500);

        IrradianceSeries series = gateway().fetch().orElseThrow();

        // Die LAUFENDE Stunde hat bereits begonnen und zaehlt damit zur Vergangenheit:
        // drei volle Stunden davor plus die angebrochene.
        assertEquals(4, series.past().size(), "drei volle Stunden plus die laufende");
        assertEquals(3, series.forecast().size(), "die drei Stunden danach");
        assertTrue(series.past().get(0).hour().isBefore(series.forecast().get(0).hour()));
        assertEquals(500.0, series.forecast().get(0).gtiWattPerSqm(), 0.0001);
    }

    @Test
    void ueberspringtNullWerteStattSieAlsNullZuLesen() {
        // Open-Meteo liefert fuer einzelne Stunden null; 0 hiesse "keine Sonne" und
        // wuerde einen Lern-Datenpunkt verfaelschen.
        body = """
                {"timezone":"Europe/Zurich","hourly":{
                  "time":["2020-06-01T10:00","2020-06-01T11:00","2020-06-01T12:00"],
                  "global_tilted_irradiance":[400,null,600]}}""";

        IrradianceSeries series = gateway().fetch().orElseThrow();

        assertEquals(2, series.past().size());
        assertEquals(400.0, series.past().get(0).gtiWattPerSqm(), 0.0001);
        assertEquals(600.0, series.past().get(1).gtiWattPerSqm(), 0.0001);
    }

    @Test
    void rechnetLokaleZeitMitDerZoneAusDemResponseUm() {
        // 12:00 lokal im Sommer = 10:00 UTC (Europe/Zurich, UTC+2).
        body = """
                {"timezone":"Europe/Zurich","hourly":{
                  "time":["2020-06-01T12:00"],"global_tilted_irradiance":[700]}}""";

        IrradianceSeries series = gateway().fetch().orElseThrow();

        assertEquals(Instant.parse("2020-06-01T10:00:00Z"), series.past().get(0).hour());
    }

    @Test
    void httpFehlerLiefertLeer() {
        status = 503;
        body = "kaputt";

        assertEquals(Optional.empty(), gateway().fetch());
    }

    @Test
    void unlesbaresJsonLiefertLeer() {
        body = "{kein json";

        assertEquals(Optional.empty(), gateway().fetch());
    }

    @Test
    void fehlenderHourlyBlockErgibtLeereReiheAberKeinenFehler() {
        body = """
                {"timezone":"Europe/Zurich","latitude":46.875}""";

        IrradianceSeries series = gateway().fetch().orElseThrow();

        assertTrue(series.isEmpty());
        assertFalse(series.fetchedAt() == null);
    }

    @Test
    void unerreichbarerEndpunktLiefertLeer() {
        server.stop(0);
        server = null;

        assertEquals(Optional.empty(), gateway().fetch());
    }
}
