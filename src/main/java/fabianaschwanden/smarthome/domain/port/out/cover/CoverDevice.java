package fabianaschwanden.smarthome.domain.port.out.cover;

import fabianaschwanden.smarthome.domain.model.cover.CoverCommand;

import java.util.OptionalInt;

/**
 * Getriebener Port: eine physische Store/Jalousie im LAN. Adapter in
 * {@code adapter/out/cover} implementieren diesen Port (Tuya-LAN bzw. Mock).
 */
public interface CoverDevice {

    String id();

    String name();

    String room();

    /** Grundbefehl (Auf/Ab/Stopp) ausführen. */
    void apply(CoverCommand command);

    /** Auf eine Position fahren (0..100). */
    void setPosition(int position);

    /**
     * Liest die aktuelle Position vom Gerät.
     *
     * @return Position 0..100, oder {@code empty}, wenn nicht erreichbar/unbekannt.
     */
    OptionalInt readPosition();
}
