package fabianaschwanden.smarthome.adapter.out.alert;

import fabianaschwanden.smarthome.domain.model.alert.AlertSettings;
import fabianaschwanden.smarthome.domain.port.out.alert.AlertPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Versendet Push-Benachrichtigungen über <a href="https://ntfy.sh">ntfy.sh</a>:
 * ein HTTP-POST an {@code <base>/<topic>}, Text als Body, Titel/Priorität als Header.
 * Auf dem Handy abonniert die ntfy-App denselben Topic.
 *
 * <p>Die Basis-URL ist konfigurierbar ({@code alert.ntfy.base-url}, Default
 * {@code https://ntfy.sh}) – für einen selbstgehosteten ntfy-Server.
 */
@ApplicationScoped
public class NtfyAlertPublisher implements AlertPublisher {

    private static final Logger LOG = Logger.getLogger(NtfyAlertPublisher.class);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String baseUrl;

    public NtfyAlertPublisher(
            @ConfigProperty(name = "alert.ntfy.base-url", defaultValue = "https://ntfy.sh") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public boolean publish(AlertSettings settings, String title, String message, boolean priority) {
        if (!settings.canPush()) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/" + settings.ntfyTopic()))
                    .timeout(Duration.ofSeconds(8))
                    // Header-Werte müssen ASCII sein; Umlaute o. Ä. würden sonst abgelehnt.
                    .header("Title", asciiHeader(title))
                    .header("Priority", priority ? "urgent" : "default")
                    .header("Tags", priority ? "rotating_light" : "house")
                    .POST(HttpRequest.BodyPublishers.ofString(message, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> resp = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            boolean ok = resp.statusCode() >= 200 && resp.statusCode() < 300;
            if (!ok) {
                LOG.warnf("ntfy-Push fehlgeschlagen: HTTP %d (Topic %s)", resp.statusCode(), settings.ntfyTopic());
            }
            return ok;
        } catch (Exception e) {
            LOG.warnf(e, "ntfy-Push nicht möglich (Topic %s)", settings.ntfyTopic());
            return false;
        }
    }

    /** Header dürfen nur ASCII enthalten; Nicht-ASCII (z. B. Umlaute) durch '?' ersetzen. */
    private static String asciiHeader(String value) {
        return value.replaceAll("[^\\x20-\\x7E]", "?");
    }
}
