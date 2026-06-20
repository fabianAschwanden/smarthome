package fabianaschwanden.smarthome.adapter.out.battery.smartfox;

import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.port.out.battery.RelaySwitch;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Schaltet das SMARTFOX-Relais 1 per HTTP: {@code GET <relay-url>}, wobei
 * {@code {state}} durch den state-Code ersetzt wird. Die Codes für EIN/AUS sind
 * konfigurierbar, da firmware-abhängig (siehe SPEC §2, gegen die reale Anlage
 * verifiziert: {@code state=1} schaltet manuell EIN, {@code state=0} AUS).
 */
@ApplicationScoped
@IfBuildProperty(name = "smarthome.real-devices", stringValue = "true")
public class SmartfoxRelaySwitch implements RelaySwitch {

    private static final Logger LOG = Logger.getLogger(SmartfoxRelaySwitch.class);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String relayUrlTemplate;
    private final String stateOn;
    private final String stateOff;

    public SmartfoxRelaySwitch(
            @ConfigProperty(name = "battery.smartfox.relay-url") String relayUrlTemplate,
            @ConfigProperty(name = "battery.smartfox.state-on", defaultValue = "1") String stateOn,
            @ConfigProperty(name = "battery.smartfox.state-off", defaultValue = "0") String stateOff) {
        this.relayUrlTemplate = relayUrlTemplate;
        this.stateOn = stateOn;
        this.stateOff = stateOff;
    }

    @Override
    public void apply(RelayState state) {
        String url = relayUrlTemplate.replace("{state}", state == RelayState.ON ? stateOn : stateOff);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            LOG.infof("SMARTFOX-Relais geschaltet: %s", state);
        } catch (Exception e) {
            // Steuerbefehle dürfen nicht still verschwinden: hochreichen, damit der
            // Aufrufer (REST 5xx bzw. Auto-Tick-Log) den Fehlschlag sieht.
            throw new RelaySwitchFailed(state, e);
        }
    }
}
