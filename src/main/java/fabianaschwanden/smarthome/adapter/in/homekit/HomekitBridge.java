package fabianaschwanden.smarthome.adapter.in.homekit;

import io.github.hapjava.server.impl.HomekitRoot;
import io.github.hapjava.server.impl.HomekitServer;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Driving Adapter — veröffentlicht die Geräte als HomeKit-Bridge im LAN (HAP über mDNS),
 * sodass Siri, die Home-App und Apple-Automationen sie bedienen können.
 *
 * <p><b>Stand: Spike (PLAN Etappe 1).</b> Die Bridge trägt bislang nur einen
 * Dummy-Schalter. Er beweist die Kette iPhone → mDNS → HAP-Server → Java-Code, bevor
 * echte Geräte angebunden werden; die Accessories der Etappen 3–5 rufen dann die
 * vorhandenen Driving Ports.
 *
 * <p>Bewusst eine <b>Laufzeit</b>-Property statt {@code @IfBuildProperty}: So lässt sich
 * die Bridge auch im Mock-Modus starten und mit dem iPhone gegen Mock-Geräte koppeln –
 * Entwickeln ohne Anlage (SPEC §5). Im Testprofil bleibt sie aus, im CI gibt es kein mDNS.
 */
@ApplicationScoped
public class HomekitBridge {

    private static final Logger LOG = Logger.getLogger(HomekitBridge.class);

    private final boolean enabled;
    private final String name;
    private final int port;
    private final Optional<String> pin;

    private HomekitServer server;
    private HomekitRoot bridge;

    public HomekitBridge(
            @ConfigProperty(name = "homekit.enabled", defaultValue = "false") boolean enabled,
            @ConfigProperty(name = "homekit.name", defaultValue = "Smarthome") String name,
            @ConfigProperty(name = "homekit.port", defaultValue = "9123") int port,
            @ConfigProperty(name = "homekit.pin") Optional<String> pin) {
        this.enabled = enabled;
        this.name = name;
        this.port = port;
        this.pin = pin;
    }

    void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            return;
        }
        if (pin.isEmpty() || pin.get().isBlank()) {
            // Ohne Setup-Code kann niemand koppeln. Lieber laut und aus, als eine Bridge,
            // die sichtbar ist und beim Koppeln scheitert.
            LOG.warn("HomeKit ist aktiviert, aber homekit.pin fehlt – Bridge wird nicht gestartet");
            return;
        }
        try {
            server = new HomekitServer(port);
            bridge = server.createBridge(
                    new InMemoryAuthInfo(pin.get()),
                    name,
                    2, // Kategorie 2 = Bridge
                    "smarthome",
                    "Bridge",
                    "1",
                    "1.0",
                    "1.0");
            bridge.addAccessory(new DummySwitchAccessory(2, "Spike-Schalter"));
            bridge.start();
            LOG.infof("HomeKit-Bridge '%s' gestartet auf Port %d – Kopplung mit dem Setup-Code",
                    name, port);
        } catch (Exception e) {
            // Die Bridge ist ein Zusatzkanal: Wenn sie nicht startet, muss die App
            // trotzdem laufen. Dashboard und REST haengen nicht an ihr.
            LOG.errorf(e, "HomeKit-Bridge konnte nicht gestartet werden: %s", e.getMessage());
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        if (bridge != null) {
            bridge.stop();
        }
        if (server != null) {
            server.stop();
        }
    }
}
