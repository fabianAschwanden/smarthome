package fabianaschwanden.smarthome.domain.port.out.safety;

import java.util.List;

/**
 * Getriebener Port: liefert die konfigurierten Rauchmelder. Der Adapter entscheidet
 * (per Build-Property), ob echte Geräte oder Mock-Geräte erzeugt werden.
 */
public interface SmokeDetectorDeviceFactory {

    List<SmokeDetectorDevice> devices();
}
