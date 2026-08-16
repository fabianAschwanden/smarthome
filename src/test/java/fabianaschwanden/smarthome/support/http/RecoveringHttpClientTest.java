package fabianaschwanden.smarthome.support.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Der selbstheilende HTTP-Client gegen einen echten kleinen Server.
 *
 * <p>Anlass war ein Ausfall in Produktion: Der lange gehaltene Client verlor seinen
 * Selector-Manager, danach scheiterten Schalten UND Lesen des Batterie-Relais dauerhaft.
 */
class RecoveringHttpClientTest {

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private HttpRequest request() {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
    }

    @Test
    void liefert_die_antwort_wie_ein_gewoehnlicher_client() throws Exception {
        RecoveringHttpClient client = new RecoveringHttpClient(Duration.ofSeconds(2));

        HttpResponse<String> response = client.send(request(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("ok", response.body());
    }

    @Test
    void bleibt_ueber_viele_aufrufe_benutzbar() throws Exception {
        RecoveringHttpClient client = new RecoveringHttpClient(Duration.ofSeconds(2));

        for (int i = 0; i < 5; i++) {
            assertEquals(200, client.send(request(), HttpResponse.BodyHandlers.ofString()).statusCode());
        }

        assertEquals(5, requests.get());
    }

    @Test
    void reicht_gewoehnliche_fehler_weiter() {
        // Ein nicht erreichbarer Port ist ein echter Fehler und keine Sache fuer einen
        // neuen Client - er muss beim Aufrufer ankommen.
        RecoveringHttpClient client = new RecoveringHttpClient(Duration.ofMillis(200));
        HttpRequest tot = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:1/"))
                .timeout(Duration.ofMillis(300))
                .GET()
                .build();

        assertThrows(IOException.class, () -> client.send(tot, HttpResponse.BodyHandlers.ofString()));
    }
}
