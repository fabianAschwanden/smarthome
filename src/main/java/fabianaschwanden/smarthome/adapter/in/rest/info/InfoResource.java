package fabianaschwanden.smarthome.adapter.in.rest.info;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Driving Adapter — liefert die Release-Info der laufenden Instanz (Version, Build-Zeit)
 * aus der Config. Rein operativ, keine Geschäftslogik und damit kein Use-Case-Port.
 */
@Path("/api/info")
@Produces(MediaType.APPLICATION_JSON)
public class InfoResource {

    private final String version;
    private final String builtAt;

    // builtAt als Optional: ein leerer Wert (APP_BUILT_AT ungesetzt, lokale Builds) würde
    // sonst beim Start als null-Konvertierung scheitern.
    public InfoResource(
            @ConfigProperty(name = "app.version", defaultValue = "dev") String version,
            @ConfigProperty(name = "app.built-at") Optional<String> builtAt) {
        this.version = version;
        this.builtAt = builtAt.orElse("");
    }

    @GET
    public AppInfoDto info() {
        return new AppInfoDto(version, builtAt);
    }
}
