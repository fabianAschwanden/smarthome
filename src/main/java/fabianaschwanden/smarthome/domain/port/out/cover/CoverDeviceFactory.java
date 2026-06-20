package fabianaschwanden.smarthome.domain.port.out.cover;

import java.util.List;

/**
 * Getriebener Port: liefert die konfigurierten Storen. Der Adapter entscheidet
 * (per Build-Property), ob echte LAN-Geräte oder Mock-Geräte erzeugt werden.
 */
public interface CoverDeviceFactory {

    List<CoverDevice> devices();
}
