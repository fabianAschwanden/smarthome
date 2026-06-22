package fabianaschwanden.smarthome.adapter.out.energy.smartfox;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Testet den SMARTFOX-Energie-Adapter gegen einen lokalen Fake-HTTP-Server, der
 * {@code /values.xml} bedient (kein echtes Gerät). Der Adapter wird direkt
 * instanziiert – die {@code @IfBuildProperty}-Aktivierung ist eine Build-Zeit-Frage
 * und für den Parsing-Test nicht nötig.
 *
 * <p>{@code @QuarkusTest}, damit die Coverage ins Quarkus-JaCoCo zählt.
 */
@QuarkusTest
class SmartfoxEnergySourceTest {

    private HttpServer server;
    private String baseUrl;
    private volatile String body = "";
    private volatile int status = 200;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/values.xml", exchange -> {
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/xml");
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
    void liestUndMapptDieValuesXml() {
        status = 200;
        // PV = 3000 W, Netz = -500 W (Einspeisung), Batterie = 200 W (Laden).
        // Verbrauch = PV + Netz - Batterie = 3000 + (-500) - 200 = 2300 W.
        body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <values>
              <value id="hidProduction">3000 W</value>
              <value id="detailsPowerValue">-500 W</value>
              <value id="hidBatteryPower">200 W</value>
            </values>
            """;
        SmartfoxEnergySource src = new SmartfoxEnergySource(baseUrl);

        assertEquals(PowerSource.SMARTFOX, src.source());
        PowerReading r = src.read();
        assertEquals(PowerSource.SMARTFOX, r.source());
        assertEquals("OK", r.status().name());
        assertEquals(3000.0, r.pvWatt());
        assertEquals(-500.0, r.gridWatt());
        assertEquals(200.0, r.batteryWatt());
        assertEquals(2300.0, r.consumptionWatt());
    }

    @Test
    void skaliertKilowattAufWatt() {
        status = 200;
        // kW-Einheit muss ×1000 skaliert werden; Komma als Dezimaltrenner.
        body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <values>
              <value id="hidProduction">2,5 kW</value>
              <value id="detailsPowerValue">1,0 kW</value>
            </values>
            """;
        SmartfoxEnergySource src = new SmartfoxEnergySource(baseUrl);
        PowerReading r = src.read();
        assertEquals("OK", r.status().name());
        assertEquals(2500.0, r.pvWatt());
        assertEquals(1000.0, r.gridWatt());
        // hidBatteryPower fehlt -> batteryWatt ist null, battery() in Bilanz = 0.
        assertNull(r.batteryWatt());
        assertEquals(3500.0, r.consumptionWatt());
    }

    @Test
    void fehlendeWerteErgebenNull() {
        status = 200;
        body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <values>
              <value id="irgendwas">42 W</value>
            </values>
            """;
        SmartfoxEnergySource src = new SmartfoxEnergySource(baseUrl);
        PowerReading r = src.read();
        assertEquals("OK", r.status().name());
        assertEquals(0.0, r.pvWatt());
        assertEquals(0.0, r.gridWatt());
        assertNull(r.batteryWatt());
        assertEquals(0.0, r.consumptionWatt());
        assertNotNull(r.timestamp());
    }

    @Test
    void httpFehlerLiefertErrorReading() {
        status = 500;
        body = "";
        SmartfoxEnergySource src = new SmartfoxEnergySource(baseUrl);
        PowerReading r = src.read();
        assertEquals("ERROR", r.status().name());
    }

    @Test
    void kaputtesXmlLiefertErrorReading() {
        status = 200;
        body = "<values><value id=\"hidProduction\">3000 W"; // unvollständig
        SmartfoxEnergySource src = new SmartfoxEnergySource(baseUrl);
        PowerReading r = src.read();
        assertEquals("ERROR", r.status().name());
    }

    @Test
    void unerreichbarLiefertErrorReading() {
        SmartfoxEnergySource src = new SmartfoxEnergySource("http://127.0.0.1:1");
        PowerReading r = src.read();
        assertEquals("ERROR", r.status().name());
        assertEquals(PowerSource.SMARTFOX, r.source());
    }
}
