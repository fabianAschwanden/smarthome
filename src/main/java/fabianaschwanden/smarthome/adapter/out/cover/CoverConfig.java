package fabianaschwanden.smarthome.adapter.out.cover;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

/**
 * Konfiguration der Storen (beliebig viele) unter {@code cover.devices[i].*}.
 * local-keys sind Geheimnisse und gehören in die gitignorete config-Datei, nicht
 * ins Repo (siehe docs/cover/SPEC.md).
 */
@ConfigMapping(prefix = "cover")
public interface CoverConfig {

    List<Device> devices();

    interface Device {
        String id();

        String name();

        @WithDefault("")
        String room();

        String deviceId();

        Optional<String> localKey();

        /** LAN-IP als Startwert (optional); TuyaDiscovery liefert die aktuelle IP. */
        Optional<String> address();

        default String addressOrDiscovery() {
            return address().filter(a -> !a.isBlank()).orElse("0.0.0.0");
        }

        @WithDefault("3.3")
        String version();

        /** Tuya-dp für den Grundbefehl (open/close/stop), meist 1. */
        @WithDefault("1")
        int controlDp();

        /** Tuya-dp für die Soll-Position (percent_control), meist 2. */
        @WithDefault("2")
        int positionDp();

        /** Tuya-dp für die Ist-Position (percent_state), meist 3. */
        @WithDefault("3")
        int stateDp();

        default Optional<String> localKeyIfPresent() {
            return localKey().filter(k -> !k.isBlank());
        }
    }
}
