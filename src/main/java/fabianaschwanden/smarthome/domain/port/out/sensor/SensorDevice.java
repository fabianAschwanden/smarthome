package fabianaschwanden.smarthome.domain.port.out.sensor;

import java.util.Optional;

/**
 * Getriebener Port: ein physischer Umweltsensor im LAN. Adapter in
 * {@code adapter/out/sensor} implementieren diesen Port (Tuya-LAN bzw. Mock).
 */
public interface SensorDevice {

    String id();

    String name();

    String room();

    /** Liest die Messwerte; {@code empty}, wenn der Sensor nicht erreichbar ist. */
    Optional<Reading> read();

    /** Messwerte: Temperatur in °C, Luftfeuchte in %. */
    record Reading(double temperature, int humidity) {
    }
}
