package fabianaschwanden.smarthome.domain.port.out.safety;

import fabianaschwanden.smarthome.domain.model.safety.AlarmState;

import java.util.Optional;

/**
 * Getriebener Port: ein physischer Rauchmelder im LAN. Adapter in
 * {@code adapter/out/safety} implementieren diesen Port (Tuya-LAN bzw. Mock).
 */
public interface SmokeDetectorDevice {

    String id();

    String name();

    String room();

    /** Liest Alarm + Batterie; {@code empty}, wenn der Melder nicht erreichbar ist. */
    Optional<Reading> read();

    /** Messwerte: Alarmzustand und Batteriestand in % (-1 = unbekannt). */
    record Reading(AlarmState alarm, int battery) {
    }
}
