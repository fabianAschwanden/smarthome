package fabianaschwanden.smarthome.adapter.out.energy.fronius;

import fabianaschwanden.smarthome.domain.model.energy.PowerReading;
import fabianaschwanden.smarthome.domain.model.energy.PowerSource;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySourceGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Liest den Fronius über die lokale Solar API (JSON). Endpoint:
 * {@code GET <base-url>/solar_api/v1/GetPowerFlowRealtimeData.fcgi}.
 * Feld-Mapping und Vorzeichen siehe SPEC §3.1/§4; gegen die reale Anlage zu verifizieren (SPEC §11).
 *
 * <p>Aktiv, wenn {@code smarthome.real-devices=true} UND {@code smarthome.fronius.enabled=true}
 * (Default). Mit {@code smarthome.fronius.enabled=false} lässt sich der Fronius gezielt
 * ausblenden, ohne SMARTFOX/Tuya zu berühren – dann liefert das Dashboard nur den SMARTFOX.
 */
@ApplicationScoped
@IfBuildProperty(name = "smarthome.real-devices", stringValue = "true")
@IfBuildProperty(name = "smarthome.fronius.enabled", stringValue = "true", enableIfMissing = true)
public class FroniusEnergySource implements EnergySourceGateway {

    private static final Logger LOG = Logger.getLogger(FroniusEnergySource.class);
    private static final String PATH = "/solar_api/v1/GetPowerFlowRealtimeData.fcgi";

    private final String baseUrl;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public FroniusEnergySource(
            @ConfigProperty(name = "energy.fronius.base-url") String baseUrl,
            ObjectMapper mapper) {
        this.baseUrl = baseUrl;
        this.mapper = mapper;
    }

    @Override
    public PowerSource source() {
        return PowerSource.FRONIUS;
    }

    @Override
    public PowerReading read() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + PATH))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.warnf("Fronius HTTP %d", response.statusCode());
                return PowerReading.error(PowerSource.FRONIUS, Instant.now());
            }
            JsonNode site = mapper.readTree(response.body()).path("Body").path("Data").path("Site");
            double grid = site.path("P_Grid").asDouble(0);       // + Bezug, - Einspeisung
            double pv = Math.max(0, site.path("P_PV").asDouble(0)); // null bei Nacht
            Double battery = site.hasNonNull("P_Akku") ? site.get("P_Akku").asDouble() : null;
            double consumption = Math.abs(site.path("P_Load").asDouble(0)); // P_Load i.d.R. negativ
            PowerReading.DailyEnergy daily = new PowerReading.DailyEnergy(
                    nullableDouble(site, "E_Day"),               // Wh heute (kann null sein)
                    nullableDouble(site, "E_Total"),             // Wh gesamt
                    nullableDouble(site, "rel_Autonomy"),        // Selbstversorgung %
                    nullableDouble(site, "rel_SelfConsumption")); // Eigennutzung %
            return PowerReading.of(PowerSource.FRONIUS, Instant.now(), grid, pv, battery, consumption, daily);
        } catch (Exception e) {
            LOG.warnf("Fronius nicht lesbar: %s", e.getMessage());
            return PowerReading.error(PowerSource.FRONIUS, Instant.now());
        }
    }

    /** Liest ein numerisches Feld; {@code null}, wenn fehlend oder JSON-null (z. B. E_Day nachts). */
    private static Double nullableDouble(JsonNode site, String field) {
        return site.hasNonNull(field) ? site.get(field).asDouble() : null;
    }
}
