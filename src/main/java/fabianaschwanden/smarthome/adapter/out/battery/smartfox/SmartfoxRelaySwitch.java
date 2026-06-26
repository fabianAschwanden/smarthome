package fabianaschwanden.smarthome.adapter.out.battery.smartfox;

import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.port.out.battery.RelaySwitch;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Schaltet das SMARTFOX-Relais 1 per HTTP: {@code GET <relay-url>}, wobei
 * {@code {state}} durch den state-Code ersetzt wird. Die Codes für EIN/AUS sind
 * konfigurierbar, da firmware-abhängig (siehe SPEC §2, gegen die reale Anlage
 * verifiziert: {@code state=1} schaltet manuell EIN, {@code state=0} AUS).
 *
 * <p>{@link #read()} liest den Ist-Zustand aus {@code values.xml}: das Feld
 * {@code relais1State} liefert Klartext {@code EIN}/{@code AUS} (Relais 1 = Batterie).
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
    private final String valuesUrl;
    private final String stateField;

    public SmartfoxRelaySwitch(
            @ConfigProperty(name = "battery.smartfox.relay-url") String relayUrlTemplate,
            @ConfigProperty(name = "battery.smartfox.state-on", defaultValue = "1") String stateOn,
            @ConfigProperty(name = "battery.smartfox.state-off", defaultValue = "0") String stateOff,
            @ConfigProperty(name = "energy.smartfox.base-url") String baseUrl,
            @ConfigProperty(name = "battery.smartfox.state-field", defaultValue = "relais1State") String stateField) {
        this.relayUrlTemplate = relayUrlTemplate;
        this.stateOn = stateOn;
        this.stateOff = stateOff;
        this.valuesUrl = baseUrl + "/values.xml";
        this.stateField = stateField;
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

    @Override
    public Optional<RelayState> read() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(valuesUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                LOG.warnf("SMARTFOX values.xml: HTTP %d – Relais-Zustand nicht lesbar", response.statusCode());
                return Optional.empty();
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            try (InputStream body = response.body()) {
                Document doc = builder.parse(body);
                String raw = valueById(doc, stateField);
                return toState(raw);
            }
        } catch (Exception e) {
            // Lesen ist optional (Start-Initialisierung) – kein harter Fehler.
            LOG.debugf(e, "SMARTFOX-Relais-Zustand nicht lesbar (%s)", valuesUrl);
            return Optional.empty();
        }
    }

    /** Liefert den Text des {@code <value id="...">}-Elements oder {@code null}. */
    private static String valueById(Document doc, String id) {
        NodeList nodes = doc.getElementsByTagName("value");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (id.equals(element.getAttribute("id"))) {
                return element.getTextContent();
            }
        }
        return null;
    }

    /** SMARTFOX liefert den Relais-Status als Klartext EIN/AUS (relais1State). */
    private static Optional<RelayState> toState(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String v = raw.trim().toUpperCase();
        if (v.equals("EIN") || v.equals("ON") || v.equals("1")) {
            return Optional.of(RelayState.ON);
        }
        if (v.equals("AUS") || v.equals("OFF") || v.equals("0")) {
            return Optional.of(RelayState.OFF);
        }
        // z. B. "x" (unbekannt/inaktiv) -> kein verlässlicher Zustand.
        return Optional.empty();
    }
}
