package fabianaschwanden.smarthome.domain.port.out.tuya;

import java.util.List;

/**
 * Getriebener Port: liefert die konfigurierten Schaltgeräte. Der Adapter entscheidet
 * (per Profil/Build-Property), ob echte LAN-Geräte oder Mock-Geräte erzeugt werden.
 * So bleibt der Application-Service frei von Konfigurations- und Adapter-Wissen.
 */
public interface SwitchDeviceFactory {

    /** Alle konfigurierten Geräte als Driven-Port-Instanzen. */
    List<SwitchDevice> devices();
}
