package fabianaschwanden.smarthome.domain.model.battery;

/**
 * Ist-Ablesung des Batterie-Relais: der am Gerät eingestellte Modus und der aktuelle
 * Relais-Zustand. Das SMARTFOX-Relais 1 ist dreiwertig (Aus / Manuell / Automatik) und
 * wird hier auf das Domänen-Paar abgebildet: Aus = ({@code MANUAL}, {@code OFF}),
 * Manuell = ({@code MANUAL}, {@code ON}), Automatik = ({@code AUTO}, Ist-Ausgang).
 *
 * <p>Value Object: immutable {@code record}.
 */
public record RelayReading(ControlMode mode, RelayState state) {
}
