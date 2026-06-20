package fabianaschwanden.smarthome.adapter.out.climate;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

/**
 * Konfiguration der Klimaanlagen unter {@code climate.devices[i].*}. Die reale
 * Schnittstelle steht noch nicht fest; {@code address}/{@code secret} sind für den
 * späteren echten Adapter vorbereitet.
 */
@ConfigMapping(prefix = "climate")
public interface ClimateConfig {

    List<Device> devices();

    interface Device {
        String id();

        String name();

        @WithDefault("")
        String room();

        /** Midea/NetHome-Plus-Geräte-ID (numerisch). */
        Optional<String> deviceId();

        /** Midea-Token (V3-Authentifizierung). */
        Optional<String> token();

        /** Midea-Key (V3-Authentifizierung). */
        Optional<String> key();

        /** LAN-IP (optional – Discover/Config). */
        Optional<String> address();

        /** Soll-Temperatur-Bereich (°C). */
        @WithDefault("16")
        int tempMin();

        @WithDefault("30")
        int tempMax();

        /** Vollständig konfiguriert (für echten Midea-Adapter nutzbar). */
        default boolean mideaReady() {
            return deviceId().filter(s -> !s.isBlank()).isPresent()
                    && token().filter(s -> !s.isBlank()).isPresent()
                    && key().filter(s -> !s.isBlank()).isPresent()
                    && address().filter(s -> !s.isBlank()).isPresent();
        }
    }
}
