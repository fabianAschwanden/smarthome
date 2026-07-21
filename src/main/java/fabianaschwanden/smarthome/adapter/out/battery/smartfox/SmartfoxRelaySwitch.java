package fabianaschwanden.smarthome.adapter.out.battery.smartfox;

import fabianaschwanden.smarthome.domain.model.battery.ControlMode;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.battery.RelayReading;
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
 * Schaltet das dreiwertige SMARTFOX-Relais 1 (Batterie) per HTTP: {@code GET <relay-url>}
 * mit {@code {state}} = firmware-abhängiger Code. Gegen die reale Anlage verifiziert
 * (setswrel.cgi?rel=1&amp;state=…):
 * <ul>
 *   <li>{@code state=1} → Manuell EIN</li>
 *   <li>{@code state=2} → Aus</li>
 *   <li>{@code state=0} → Automatik (geräteeigene Überschuss-Steuerung)</li>
 * </ul>
 *
 * <p>{@link #read()} liest {@code hidR1Mode} aus {@code values.xml}: {@code x}=Aus,
 * {@code m}=Manuell, {@code 0}=Automatik/nicht ladend, {@code 1}=Automatik/ladend.
 */
@ApplicationScoped
@IfBuildProperty(name = "smarthome.real-devices", stringValue = "true")
public class SmartfoxRelaySwitch implements RelaySwitch {

    private static final Logger LOG = Logger.getLogger(SmartfoxRelaySwitch.class);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String relayUrlTemplate;
    private final String stateManualOn;
    private final String stateOff;
    private final String stateAuto;
    private final String valuesUrl;
    private final String stateField;

    public SmartfoxRelaySwitch(
            @ConfigProperty(name = "battery.smartfox.relay-url") String relayUrlTemplate,
            @ConfigProperty(name = "battery.smartfox.state-on", defaultValue = "1") String stateManualOn,
            @ConfigProperty(name = "battery.smartfox.state-off", defaultValue = "2") String stateOff,
            @ConfigProperty(name = "battery.smartfox.state-auto", defaultValue = "0") String stateAuto,
            @ConfigProperty(name = "energy.smartfox.base-url") String baseUrl,
            @ConfigProperty(name = "battery.smartfox.state-field", defaultValue = "hidR1Mode") String stateField) {
        this.relayUrlTemplate = relayUrlTemplate;
        this.stateManualOn = stateManualOn;
        this.stateOff = stateOff;
        this.stateAuto = stateAuto;
        this.valuesUrl = baseUrl + "/values.xml";
        this.stateField = stateField;
    }

    @Override
    public void apply(ControlMode mode, RelayState state) {
        String code = code(mode, state);
        String url = relayUrlTemplate.replace("{state}", code);
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
            LOG.infof("SMARTFOX-Relais gestellt: Modus=%s Zustand=%s (state=%s)", mode, state, code);
        } catch (Exception e) {
            // Steuerbefehle dürfen nicht still verschwinden: hochreichen, damit der
            // Aufrufer (REST 5xx bzw. Log) den Fehlschlag sieht.
            throw new RelaySwitchFailed(mode, state, e);
        }
    }

    private String code(ControlMode mode, RelayState state) {
        if (mode == ControlMode.AUTO) {
            return stateAuto;
        }
        return state == RelayState.ON ? stateManualOn : stateOff;
    }

    @Override
    public Optional<RelayReading> read() {
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
                return toReading(valueById(doc, stateField));
            }
        } catch (Exception e) {
            // Lesen ist optional (Start-Initialisierung / Sync) – kein harter Fehler.
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

    /** hidR1Mode -> (Modus, Zustand). {@code x}=Aus, {@code m}=Manuell, {@code 0/1}=Automatik. */
    private static Optional<RelayReading> toReading(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return switch (raw.trim().toLowerCase()) {
            case "x" -> Optional.of(new RelayReading(ControlMode.MANUAL, RelayState.OFF));   // Aus
            case "m" -> Optional.of(new RelayReading(ControlMode.MANUAL, RelayState.ON));    // Manuell
            case "0" -> Optional.of(new RelayReading(ControlMode.AUTO, RelayState.OFF));     // Auto, nicht ladend
            case "1" -> Optional.of(new RelayReading(ControlMode.AUTO, RelayState.ON));      // Auto, ladend
            default -> Optional.empty();
        };
    }
}
