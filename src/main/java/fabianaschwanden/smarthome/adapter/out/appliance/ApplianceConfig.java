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

        /** LAN-IP der Gecko in.touch2-Steuerung (für den echten Gecko-Adapter). */
        Optional<String> address();

        /** Optionales Geheimnis/Token (nur per config/Env, nie ins Repo). */
        Optional<String> secret();

        // --- Gecko (in.touch2) Mapping: welcher Spa-Key gehört zu welcher Funktion ---

        /** Gecko-Spa-Identifier (z. B. {@code SPA68:27:...}); aus der Discovery. */
        Optional<String> geckoIdent();

        /** Gecko-Key der Pumpe für die Funktion PUMP (z. B. {@code P1}). */
        Optional<String> pumpKey();

        /** Gecko-Key der Pumpe für die Funktion MASSAGE (z. B. {@code P2}). */
        Optional<String> massageKey();

        /** Gecko-Key des Lichts für die Funktion LIGHT (z. B. {@code LI}). */
        Optional<String> lightKey();

        /** Vollständig für den echten Gecko-Adapter konfiguriert (IP + Identifier). */
        default boolean geckoReady() {
            return address().filter(s -> !s.isBlank()).isPresent()
                    && geckoIdent().filter(s -> !s.isBlank()).isPresent();
        }

        default boolean heated() {
            return functions().contains(ApplianceFunction.HEATER);
        }
    }
}
