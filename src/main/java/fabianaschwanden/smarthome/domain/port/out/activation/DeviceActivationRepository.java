package fabianaschwanden.smarthome.domain.port.out.activation;

import fabianaschwanden.smarthome.domain.model.activation.DeviceKind;

import java.time.Instant;
import java.util.Set;

/**
 * Getriebener Port: welche Geräte bewusst stillgelegt sind – über den Winter vom Strom,
 * wie das Schwimmbecken und die Klimaanlage. Gespeichert wird nur die Ausnahme
 * (stillgelegt); wer nicht drinsteht, ist in Betrieb. So braucht ein neu konfiguriertes
 * Gerät keinen Datensatz, um zu funktionieren.
 *
 * <p>{@code kind} trennt die Gerätearten (siehe {@link DeviceKind}), damit sich die IDs
 * verschiedener Slices nicht in die Quere kommen.
 */
public interface DeviceActivationRepository {

    /** IDs aller stillgelegten Geräte einer Art. */
    Set<String> deactivated(DeviceKind kind);

    void deactivate(DeviceKind kind, String deviceId, Instant at);

    void reactivate(DeviceKind kind, String deviceId);
}
