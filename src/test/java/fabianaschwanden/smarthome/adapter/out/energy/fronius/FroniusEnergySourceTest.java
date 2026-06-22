package fabianaschwanden.smarthome.adapter.out.energy.fronius;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import fabianaschwanden.smarthome.domain.model.energy.PowerReading;
import fabianaschwanden.smarthome.domain.model.energy.PowerSource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testet den Fronius-Adapter gegen einen lokalen Fake-HTTP-Server (kein echtes Gerät).
 * Der Adapter wird direkt instanziiert – die {@code @IfBuildProperty}-Aktivierung ist
 * eine Build-Zeit-Frage und für den reinen Parsing-Test nicht nötig.
 *
 * <p>{@code @QuarkusTest}, damit die Coverage ins Quarkus-JaCoCo zählt.
 */
@QuarkusTest
class FroniusEnergySourceTest {

    private HttpServer server;
    private String baseUrl;
    private volatile String body = "";
    private volatile int status = 200;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/solar_api/v1/GetPowerFlowRealtimeData.fcgi", exchange -> {
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void liestUndMapptDieSolarApi() {
        status = 200;
        body = """
            {"Body":{"Data":{"Site":{
              "P_Grid": 252.0, "P_PV": 283.0, "P_Akku": null, "P_Load": -500.0,
              "E_Day": 12000.0, "E_Total": 9000000.0,
              "rel_Autonomy": 50.0, "rel_SelfConsumption": 100.0
            }}}}
            """;
        FroniusEnergySource src = new FroniusEnergySource(baseUrl, new ObjectMapper());

        assertEquals(PowerSource.FRONIUS, src.source());
        PowerReading r = src.read();
        assertEquals(PowerSource.FRONIUS, r.source());
        assertEquals("OK", r.status().name());
        assertEquals(252.0, r.gridWatt());
        assertEquals(283.0, r.pvWatt());
        assertEquals(500.0, r.consumptionWatt()); // abs(P_Load)
        assertNotNull(r.daily());
        assertEquals(50.0, r.daily().autonomyPercent());
    }

    @Test
    void negativesPvWirdAufNullGeklemmt() {
        status = 200;
        body = "{\"Body\":{\"Data\":{\"Site\":{\"P_Grid\":-100,\"P_PV\":-5,\"P_Load\":-200}}}}";
        FroniusEnergySource src = new FroniusEnergySource(baseUrl, new ObjectMapper());
        PowerReading r = src.read();
        assertEquals(0.0, r.pvWatt());
        assertEquals(-100.0, r.gridWatt());
    }

    @Test
    void httpFehlerLiefertErrorReading() {
        status = 500;
        body = "";
        FroniusEnergySource src = new FroniusEnergySource(baseUrl, new ObjectMapper());
        PowerReading r = src.read();
        assertEquals("ERROR", r.status().name());
    }

    @Test
    void unerreichbarLiefertErrorReading() {
        // Server, der sicher nicht antwortet (geschlossener Port).
        FroniusEnergySource src = new FroniusEnergySource("http://127.0.0.1:1", new ObjectMapper());
        PowerReading r = src.read();
        assertEquals("ERROR", r.status().name());
        assertFalse(r.status().name().isBlank());
        assertTrue(true);
    }
}
