package fabianaschwanden.smarthome.domain.model.sensor;

import java.time.Instant;

/**
 * Ein aufgezeichneter Messpunkt eines Umweltsensors – Grundlage der Langzeit-Historie.
 *
 * <p>Nur belastbare Werte werden aufgezeichnet: Ein offline gemeldeter Sensor oder ein
 * Platzhalter der Domäne ({@link Sensor#VALUE_UNKNOWN}, {@link Sensor#HUMIDITY_UNKNOWN})
 * erzeugt gar keinen Punkt. Eine Lücke ist ehrlicher als eine erfundene Zahl – und in
 * jeder Auswertung harmloser.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record SensorSample(String sensorId, Instant timestamp, double temperature, int humidity) {

    public SensorSample {
        if (sensorId == null || sensorId.isBlank()) {
            throw new IllegalArgumentException("sensorId darf nicht leer sein");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp darf nicht null sein");
        }
    }
}
