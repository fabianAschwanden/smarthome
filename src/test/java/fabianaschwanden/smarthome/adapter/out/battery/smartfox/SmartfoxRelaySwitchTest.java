package fabianaschwanden.smarthome.adapter.out.battery.smartfox;

import com.sun.net.httpserver.HttpServer;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testet den SMARTFOX-Relais-Schalter gegen einen lokalen Fake-HTTP-Server, der
 * den Schaltpfad {@code /setvalue} entgegennimmt und den {@code state}-Query
 * mitschreibt sowie {@code /values.xml} für den Ist-Zustand ausliefert (kein echtes
 * Gerät). Der Adapter wird direkt instanziiert.
 *
 * <p>{@code @QuarkusTest}, damit die Coverage ins Quarkus-JaCoCo zählt.
 */
@QuarkusTest
class SmartfoxRelaySwitchTest {

    private HttpServer server;
    private String baseUrl;
    private final List<String> receivedQueries = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String valuesXml = "<root></root>";
    private volatile int valuesStatus = 200;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/setvalue", exchange -> {
            receivedQueries.add(exchange.getRequestURI().getQuery());
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.createContext("/values.xml", exchange -> {
            byte[] body = valuesXml.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(valuesStatus, valuesStatus == 200 ? body.length : -1);
            if (valuesStatus == 200) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String template() {
        return baseUrl + "/setvalue?param=relay&value={state}";
    }

    private SmartfoxRelaySwitch relay(String on, String off) {
        return new SmartfoxRelaySwitch(template(), on, off, baseUrl, "relais1State");
    }

    @Test
    void schaltetEinMitStateOnCode() {
        status = 200;
        SmartfoxRelaySwitch relay = relay("1", "0");
        relay.apply(RelayState.ON);
        assertEquals(1, receivedQueries.size());
        assertEquals("param=relay&value=1", receivedQueries.get(0));
    }

    @Test
    void schaltetAusMitStateOffCode() {
        status = 200;
        SmartfoxRelaySwitch relay = relay("1", "0");
        relay.apply(RelayState.OFF);
        assertEquals(1, receivedQueries.size());
        assertEquals("param=relay&value=0", receivedQueries.get(0));
    }

    @Test
    void respektiertKonfigurierbareStateCodes() {
        status = 200;
        SmartfoxRelaySwitch relay = relay("on", "off");
        relay.apply(RelayState.ON);
        relay.apply(RelayState.OFF);
        assertEquals(2, receivedQueries.size());
        assertEquals("param=relay&value=on", receivedQueries.get(0));
        assertEquals("param=relay&value=off", receivedQueries.get(1));
    }

    @Test
    void httpFehlerWirftRelaySwitchFailed() {
        status = 500;
        SmartfoxRelaySwitch relay = relay("1", "0");
        RelaySwitchFailed ex = assertThrows(RelaySwitchFailed.class, () -> relay.apply(RelayState.ON));
        // Statuscode steckt in der Ursachenkette.
        assertEquals(IllegalStateException.class, ex.getCause().getClass());
    }

    @Test
    void unerreichbarerHostWirftRelaySwitchFailed() {
        SmartfoxRelaySwitch relay = new SmartfoxRelaySwitch(
                "http://127.0.0.1:1/setvalue?value={state}", "1", "0",
                "http://127.0.0.1:1", "relais1State");
        assertThrows(RelaySwitchFailed.class, () -> relay.apply(RelayState.ON));
    }

    @Test
    void liestIstZustandEinAusValuesXml() {
        valuesXml = "<root><value id=\"relais1State\">EIN</value></root>";
        assertEquals(Optional.of(RelayState.ON), relay("1", "0").read());

        valuesXml = "<root><value id=\"relais1State\">AUS</value></root>";
        assertEquals(Optional.of(RelayState.OFF), relay("1", "0").read());
    }

    @Test
    void liefertLeerBeiUnbekanntemOderFehlendemFeld() {
        valuesXml = "<root><value id=\"relais1State\">x</value></root>";
        assertTrue(relay("1", "0").read().isEmpty());

        valuesXml = "<root><value id=\"andereId\">EIN</value></root>";
        assertTrue(relay("1", "0").read().isEmpty());
    }

    @Test
    void liefertLeerBeiHttpFehler() {
        valuesStatus = 500;
        assertTrue(relay("1", "0").read().isEmpty());
    }

    @Test
    void liefertLeerBeiUnerreichbaremHost() {
        SmartfoxRelaySwitch relay = new SmartfoxRelaySwitch(
                template(), "1", "0", "http://127.0.0.1:1", "relais1State");
        assertTrue(relay.read().isEmpty());
    }
}
