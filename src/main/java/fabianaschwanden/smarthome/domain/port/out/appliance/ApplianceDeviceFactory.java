package fabianaschwanden.smarthome.domain.port.out.appliance;

import java.util.List;

/**
 * Getriebener Port: liefert die konfigurierten Anlagen. Der Adapter entscheidet
 * (per Build-Property), ob echte Geräte oder Mock-Geräte erzeugt werden.
 */
public interface ApplianceDeviceFactory {

    List<ApplianceDevice> devices();
}
