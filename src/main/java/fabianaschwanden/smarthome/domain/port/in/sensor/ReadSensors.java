package fabianaschwanden.smarthome.domain.port.in.sensor;

import fabianaschwanden.smarthome.domain.model.sensor.Sensor;

import java.util.List;

/** Treiber-Port (Use Case): Umweltsensoren auslesen (nur lesend). */
public interface ReadSensors {

    List<Sensor> list();
}
