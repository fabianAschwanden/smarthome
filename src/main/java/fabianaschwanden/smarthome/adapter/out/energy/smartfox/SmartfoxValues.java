package fabianaschwanden.smarthome.adapter.out.energy.smartfox;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parst die SMARTFOX-{@code values.xml} in eine {@code id -> Rohtext}-Map und
 * liefert numerische Leistungswerte in Watt (Einheit {@code kW} wird skaliert).
 */
final class SmartfoxValues {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:[.,]\\d+)?");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final Map<String, String> values;

    private SmartfoxValues(Map<String, String> values) {
        this.values = values;
    }

    static SmartfoxValues fetch(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream body = response.body()) {
            Document doc = builder.parse(body);
            return new SmartfoxValues(toMap(doc));
        }
    }

    private static Map<String, String> toMap(Document doc) {
        Map<String, String> map = new HashMap<>();
        NodeList nodes = doc.getElementsByTagName("value");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String id = element.getAttribute("id");
            if (id != null && !id.isBlank()) {
                map.put(id, element.getTextContent());
            }
        }
        return map;
    }

    /** Numerischer Leistungswert in Watt; {@code kW} wird ×1000 skaliert. */
    Optional<Double> watt(String id) {
        String raw = values.get(id);
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = NUMBER.matcher(raw);
        if (!matcher.find()) {
            return Optional.empty();
        }
        double value = Double.parseDouble(matcher.group().replace(',', '.'));
        if (raw.toLowerCase().contains("kw")) {
            value *= 1000.0;
        }
        return Optional.of(value);
    }
}
