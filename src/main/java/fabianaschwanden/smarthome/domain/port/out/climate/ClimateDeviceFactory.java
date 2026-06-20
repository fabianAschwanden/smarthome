package fabianaschwanden.smarthome.domain.port.out.climate;

import java.util.List;

/**
 * Getriebener Port: liefert die konfigurierten Klimaanlagen. Der Adapter entscheidet
 * (per Build-Property), ob echte Geräte oder Mock-Geräte erzeugt werden.
 */
public interface ClimateDeviceFactory {

    List<ClimateDevice> devices();
}
