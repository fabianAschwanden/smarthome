package fabianaschwanden.smarthome.domain.port.out.sensor;

import java.util.List;

/**
 * Getriebener Port: liefert die konfigurierten Sensoren. Der Adapter entscheidet
 * (per Build-Property), ob echte Geräte oder Mock-Geräte erzeugt werden.
 */
public interface SensorDeviceFactory {

    List<SensorDevice> devices();
}
