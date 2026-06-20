package fabianaschwanden.smarthome.adapter.out.appliance;

import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

/**
 * Konfiguration der Wellness-Anlagen unter {@code appliance.devices[i].*}.
 * Die reale Schnittstelle steht noch nicht fest; {@code address}/{@code secret}
 * sind vorbereitet (optional) und werden vom echten Adapter genutzt, sobald er kommt.
 */
@ConfigMapping(prefix = "appliance")
public interface ApplianceConfig {

    List<Device> devices();

    interface Device {
        String id();

        String name();

        @WithDefault("")
        String room();

        /** Vorhandene Funktionen dieser Anlage. */
        List<ApplianceFunction> functions();

        /** Sollbereich der Heizung (°C). Nur relevant, wenn HEATER vorhanden ist. */
        @WithDefault("18")
        int tempMin();

        @WithDefault("40")
        int tempMax();

        /** Optionale Adresse der Steuerung (für den späteren echten Adapter). */
        Optional<String> address();

        /** Optionales Geheimnis/Token (nur per config/Env, nie ins Repo). */
        Optional<String> secret();

        default boolean heated() {
            return functions().contains(ApplianceFunction.HEATER);
        }
    }
}
