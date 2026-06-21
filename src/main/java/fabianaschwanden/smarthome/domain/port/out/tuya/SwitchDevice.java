package fabianaschwanden.smarthome.domain.port.out.tuya;

import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;

import java.util.Optional;

/**
 * Getriebener Port: ein physisches Tuya-Schaltgerät im LAN. Adapter in
 * {@code adapter/out/tuya} implementieren diesen Port (lokales Protokoll bzw. Mock).
 * Pro konfiguriertem Gerät existiert eine Instanz.
 */
public interface SwitchDevice {

    /** Stabile, technische ID (z. B. "stehlampe"). */
    String id();

    /** Anzeigename des Geräts. */
    String name();

    /** Raum-Zuordnung (kann leer sein). */
    String room();

    /** Kritischer Schalter: AUS erfordert Bestätigung (versorgt z. B. das WLAN). */
    boolean critical();

    /** Optionaler Hinweis zum Schalter (Bedien-Notiz); leer, wenn keiner gesetzt ist. */
    default String hint() {
        return "";
    }

    /** Schaltet das Gerät auf den gewünschten Zustand. */
    void apply(SwitchState state);

    /**
     * Liest den Ist-Zustand vom Gerät.
     *
     * @return der Zustand, oder {@code empty}, wenn das Gerät nicht erreichbar ist.
     */
    Optional<SwitchState> readState();
}
