package fabianaschwanden.smarthome.adapter.out.safety;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

/**
 * Konfiguration der Rauchmelder unter {@code safety.smoke[i].*}. IP optional
 * (TuyaDiscovery liefert die aktuelle); local-key nur per config/Env. Standard-dps
 * eines Tuya-Rauchmelders: status=1 ("alarm"/"normal"), battery=15 (%).
 */
@ConfigMapping(prefix = "safety")
public interface SafetyConfig {

    List<Device> smoke();

    interface Device {
        String id();

        String name();

        @WithDefault("")
        String room();

        @WithDefault("unconfigured")
        String deviceId();

        Optional<String> localKey();

        Optional<String> address();

        @WithDefault("3.3")
        String version();

        /** dp des Rauch-Status ("alarm"/"normal"). */
        @WithDefault("1")
        int statusDp();

        /** dp des Batteriestands (%). */
        @WithDefault("15")
        int batteryDp();

        default Optional<String> localKeyIfPresent() {
            return localKey().filter(k -> !k.isBlank());
        }

        default String addressOrDiscovery() {
            return address().filter(a -> !a.isBlank()).orElse("0.0.0.0");
        }
    }
}
