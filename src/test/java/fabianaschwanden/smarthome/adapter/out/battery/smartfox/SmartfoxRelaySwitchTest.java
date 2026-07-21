package fabianaschwanden.smarthome.adapter.out.battery.smartfox;

import com.sun.net.httpserver.HttpServer;
import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.battery.RelayReading;
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
 * Testet den dreiwertigen SMARTFOX-Relais-Schalter gegen einen lokalen Fake-HTTP-Server:
 * der Schaltpfad schreibt den {@code state}-Query mit, {@code /values.xml} liefert
 * {@code hidR1Mode} für den Ist-Zustand. Der Adapter wird direkt instanziiert.
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

    /** Standard-Codes: Manuell-Ein=1, Aus=2, Automatik=0. */
    private SmartfoxRelaySwitch relay() {
        return new SmartfoxRelaySwitch(template(), "1", "2", "0", baseUrl, "hidR1Mode");
    }

    private String mode(String hidR1Mode) {
        return "<root><value id=\"hidR1Mode\">" + hidR1Mode + "</value></root>";
    }

    @Test
    void schreibtManuellEin() {
        relay().apply(ControlMode.MANUAL, RelayState.ON);
        assertEquals("param=relay&value=1", receivedQueries.get(0));
    }

    @Test
    void schreibtAus() {
        relay().apply(ControlMode.MANUAL, RelayState.OFF);
        assertEquals("param=relay&value=2", receivedQueries.get(0));
    }

    @Test
    void schreibtAutomatikUndIgnoriertZustand() {
        relay().apply(ControlMode.AUTO, RelayState.OFF);
        relay().apply(ControlMode.AUTO, RelayState.ON);
        assertEquals("param=relay&value=0", receivedQueries.get(0));
        assertEquals("param=relay&value=0", receivedQueries.get(1));
    }

    @Test
    void respektiertKonfigurierbareCodes() {
        SmartfoxRelaySwitch relay =
                new SmartfoxRelaySwitch(template(), "on", "off", "auto", baseUrl, "hidR1Mode");
        relay.apply(ControlMode.MANUAL, RelayState.ON);
        relay.apply(ControlMode.MANUAL, RelayState.OFF);
        relay.apply(ControlMode.AUTO, RelayState.OFF);
        assertEquals("param=relay&value=on", receivedQueries.get(0));
        assertEquals("param=relay&value=off", receivedQueries.get(1));
        assertEquals("param=relay&value=auto", receivedQueries.get(2));
    }

    @Test
    void httpFehlerWirftRelaySwitchFailed() {
        status = 500;
        RelaySwitchFailed ex = assertThrows(RelaySwitchFailed.class,
                () -> relay().apply(ControlMode.MANUAL, RelayState.ON));
        assertEquals(IllegalStateException.class, ex.getCause().getClass());
    }

    @Test
    void unerreichbarerHostWirftRelaySwitchFailed() {
        SmartfoxRelaySwitch relay = new SmartfoxRelaySwitch(
                "http://127.0.0.1:1/setvalue?value={state}", "1", "2", "0",
                "http://127.0.0.1:1", "hidR1Mode");
        assertThrows(RelaySwitchFailed.class, () -> relay.apply(ControlMode.MANUAL, RelayState.ON));
    }

    @Test
    void liestModusAusHidR1Mode() {
        valuesXml = mode("x");
        assertEquals(Optional.of(new RelayReading(ControlMode.MANUAL, RelayState.OFF)), relay().read());

        valuesXml = mode("m");
        assertEquals(Optional.of(new RelayReading(ControlMode.MANUAL, RelayState.ON)), relay().read());

        valuesXml = mode("0");
        assertEquals(Optional.of(new RelayReading(ControlMode.AUTO, RelayState.OFF)), relay().read());

        valuesXml = mode("1");
        assertEquals(Optional.of(new RelayReading(ControlMode.AUTO, RelayState.ON)), relay().read());
    }

    @Test
    void liefertLeerBeiUnbekanntemOderFehlendemFeld() {
        valuesXml = mode("z");
        assertTrue(relay().read().isEmpty());

        valuesXml = "<root><value id=\"andereId\">m</value></root>";
        assertTrue(relay().read().isEmpty());
    }

    @Test
    void liefertLeerBeiHttpFehler() {
        valuesStatus = 500;
        assertTrue(relay().read().isEmpty());
    }

    @Test
    void liefertLeerBeiUnerreichbaremHost() {
        SmartfoxRelaySwitch relay = new SmartfoxRelaySwitch(
                template(), "1", "2", "0", "http://127.0.0.1:1", "hidR1Mode");
        assertTrue(relay.read().isEmpty());
    }
}
