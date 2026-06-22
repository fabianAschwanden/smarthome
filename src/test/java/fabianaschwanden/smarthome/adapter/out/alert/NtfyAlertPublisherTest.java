package fabianaschwanden.smarthome.adapter.out.alert;

import com.sun.net.httpserver.HttpServer;
import fabianaschwanden.smarthome.domain.model.alert.AlertSettings;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testet den ntfy-Publisher gegen einen lokalen Fake-HTTP-Server (kein echtes ntfy.sh).
 * Der Adapter wird direkt mit der Fake-Basis-URL instanziiert.
 *
 * <p>{@code @QuarkusTest}, damit die Coverage ins Quarkus-JaCoCo zählt.
 */
@QuarkusTest
class NtfyAlertPublisherTest {

    private HttpServer server;
    private String baseUrl;
    private volatile int status = 200;
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastMethod.set(exchange.getRequestMethod());
            lastPath.set(exchange.getRequestURI().getPath());
            // Request-Body lesen/verwerfen, damit die Verbindung sauber abschliesst.
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postetAnTopicUndMeldetErfolgBei2xx() {
        status = 200;
        NtfyAlertPublisher publisher = new NtfyAlertPublisher(baseUrl);

        boolean ok = publisher.publish(new AlertSettings(true, "mein-topic"), "Rauchalarm", "Küche", true);

        assertTrue(ok);
        assertEquals("POST", lastMethod.get());
        assertEquals("/mein-topic", lastPath.get());
    }

    @Test
    void keinRequestWennPushNichtMoeglich() {
        NtfyAlertPublisher publisher = new NtfyAlertPublisher(baseUrl);

        // disabled -> canPush() == false
        boolean ok = publisher.publish(AlertSettings.disabled(), "Titel", "Text", false);

        assertFalse(ok);
        assertNull(lastMethod.get(), "Bei !canPush() darf kein HTTP-Request abgesetzt werden");
    }

    @Test
    void httpFehlerLiefertFalse() {
        status = 500;
        NtfyAlertPublisher publisher = new NtfyAlertPublisher(baseUrl);

        boolean ok = publisher.publish(new AlertSettings(true, "topic"), "Titel", "Text", false);

        assertFalse(ok);
    }

    @Test
    void unerreichbarerServerLiefertFalse() {
        // Geschlossener Port -> Verbindungsaufbau scheitert.
        NtfyAlertPublisher publisher = new NtfyAlertPublisher("http://127.0.0.1:1");

        boolean ok = publisher.publish(new AlertSettings(true, "topic"), "Titel", "Text", false);

        assertFalse(ok);
    }

    @Test
    void trailingSlashInBaseUrlWirdNormalisiert() {
        status = 204;
        NtfyAlertPublisher publisher = new NtfyAlertPublisher(baseUrl + "/");

        boolean ok = publisher.publish(new AlertSettings(true, "abc"), "T", "M", false);

        assertTrue(ok);
        assertEquals("/abc", lastPath.get());
    }
}
