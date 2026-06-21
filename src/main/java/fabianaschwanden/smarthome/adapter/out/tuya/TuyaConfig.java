package fabianaschwanden.smarthome.adapter.out.tuya;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

/**
 * Konfiguration der Tuya-Geräte (beliebig viele) unter {@code tuya.devices[i].*}.
 * local-keys sind Geheimnisse und gehören nicht ins Repo – sie werden je Gerät per
 * Env-Var gesetzt (siehe application.properties / docs/tuya/SPEC.md).
 */
@ConfigMapping(prefix = "tuya")
public interface TuyaConfig {

    List<Device> devices();

    interface Device {
        /** Stabile, technische ID für REST/UI (z. B. "stehlampe"). */
        String id();

        /** Anzeigename (z. B. "Stehlampe"). */
        String name();

        /** Raum-Zuordnung für die Anzeige (z. B. "Wohnzimmer"). */
        @WithDefault("")
        String room();

        /** Tuya-Geräte-ID (devId). */
        String deviceId();

        /** local-key (AES); leer/fehlend = noch nicht hinterlegt -> Gerät nicht steuerbar. */
        Optional<String> localKey();

        /** LAN-IP als Startwert (optional); bei DHCP-Wandern liefert TuyaDiscovery die
         *  aktuelle IP. Leer/fehlend ist erlaubt -> Discovery übernimmt. */
        Optional<String> address();

        default String addressOrDiscovery() {
            return address().filter(a -> !a.isBlank()).orElse("0.0.0.0");
        }

        /** Protokollversion (aktuell unterstützt: 3.3). */
        @WithDefault("3.3")
        String version();

        /** Data-Point-ID des Schalters (meist 1). */
        @WithDefault("1")
        int dp();

        /**
         * Kritischer Schalter: AUS erfordert eine ausdrückliche Bestätigung. Z. B. der
         * Homecinema-Schalter versorgt auch das WLAN – ohne ihn ist keine Steuerung mehr möglich.
         */
        @WithDefault("false")
        boolean critical();

        /** Optionaler Bedien-Hinweis zum Schalter (wird in der UI angezeigt). */
        Optional<String> hint();

        /** Hinweis-Text oder leerer String, wenn keiner gesetzt ist. */
        default String hintOrEmpty() {
            return hint().filter(h -> !h.isBlank()).orElse("");
        }

        default Optional<String> localKeyIfPresent() {
            return localKey().filter(k -> !k.isBlank());
        }
    }
}
