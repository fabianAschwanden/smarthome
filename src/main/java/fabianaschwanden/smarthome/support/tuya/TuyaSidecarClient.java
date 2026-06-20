package fabianaschwanden.smarthome.support.tuya;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Client für den lokalen tinytuya-Sidecar (siehe tools/tuya-sidecar/). Liest Geräte,
 * die Protokoll 3.4/3.5 sprechen (Session-Key-Handshake), den der reine Java-Adapter
 * noch nicht beherrscht. Gibt die rohe {@code dps}-JSON zurück.
 */
@ApplicationScoped
public class TuyaSidecarClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final String baseUrl;

    public TuyaSidecarClient(
            @ConfigProperty(name = "smarthome.tuya-sidecar-url",
                    defaultValue = "http://127.0.0.1:8765") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Liest die dps-JSON eines Geräts; {@code empty} bei Fehler/nicht erreichbar. */
    public Optional<String> readDps(String deviceId, String localKey, String ip, String version) {
        try {
            String url = baseUrl + "/read?id=" + enc(deviceId) + "&key=" + enc(localKey)
                    + "&ip=" + enc(ip) + "&version=" + enc(version);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return Optional.of(response.body());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Liest den Zustand einer Midea/NetHome-Plus-Klimaanlage; {@code empty} bei Fehler.
     * Liefert die rohe JSON {@code {"power":..,"mode":..,"target":..,"current":..,"online":..}}.
     */
    public Optional<String> readClimate(String deviceId, String token, String key, String ip) {
        return get("/climate/read?id=" + enc(deviceId) + "&token=" + enc(token)
                + "&key=" + enc(key) + "&ip=" + enc(ip));
    }

    /**
     * Steuert eine Midea/NetHome-Plus-Klimaanlage. Nur die übergebenen Parameter werden
     * gesetzt ({@code power}=true/false, {@code mode}=COOL/HEAT/AUTO/FAN, {@code target}=°C).
     * Liefert den resultierenden Zustand als JSON; {@code empty} bei Fehler.
     */
    public Optional<String> controlClimate(
            String deviceId, String token, String key, String ip,
            Boolean power, String mode, Integer target) {
        StringBuilder url = new StringBuilder("/climate/control?id=").append(enc(deviceId))
                .append("&token=").append(enc(token))
                .append("&key=").append(enc(key))
                .append("&ip=").append(enc(ip));
        if (power != null) {
            url.append("&power=").append(power);
        }
        if (mode != null) {
            url.append("&mode=").append(enc(mode));
        }
        if (target != null) {
            url.append("&target=").append(target);
        }
        return get(url.toString());
    }

    private Optional<String> get(String pathAndQuery) {
        try {
            // Grosszügig: der Sidecar wiederholt Midea-Aufrufe bei Single-Session-Resets
            // intern mehrfach mit Pause – das Backend darf nicht vorher aufgeben.
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return Optional.of(response.body());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
