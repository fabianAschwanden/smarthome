package fabianaschwanden.smarthome.adapter.out.sensor;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

/**
 * Konfiguration der Umweltsensoren unter {@code sensor.devices[i].*}. IP ist
 * optional (TuyaDiscovery liefert die aktuelle); local-key nur per config/Env.
 * Standard-dps eines Tuya-Temp/Feuchte-Sensors: temp=1 (×10), humidity=2.
 */
@ConfigMapping(prefix = "sensor")
public interface SensorConfig {

    List<Device> devices();

    interface Device {
        String id();

        String name();

        @WithDefault("")
        String room();

        String deviceId();

        Optional<String> localKey();

        Optional<String> address();

        @WithDefault("3.3")
        String version();

        /** dp der Temperatur (Rohwert, wird durch tempScale geteilt). */
        @WithDefault("1")
        int temperatureDp();

        /** dp der Luftfeuchte. */
        @WithDefault("2")
        int humidityDp();

        /** Skalierung der Roh-Temperatur (üblich 10 -> 235 = 23.5 °C). */
        @WithDefault("10")
        int temperatureScale();

        default Optional<String> localKeyIfPresent() {
            return localKey().filter(k -> !k.isBlank());
        }

        default String addressOrDiscovery() {
            return address().filter(a -> !a.isBlank()).orElse("0.0.0.0");
        }
    }
}
