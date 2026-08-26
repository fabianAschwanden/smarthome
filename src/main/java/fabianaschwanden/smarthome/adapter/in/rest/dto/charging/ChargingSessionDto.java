package fabianaschwanden.smarthome.adapter.in.rest.dto.charging;

import fabianaschwanden.smarthome.domain.model.charging.ChargingSession;

/**
 * Transport-Objekt eines Ladevorgangs.
 *
 * <p>{@code watt} ist gemessen (Verbrauchssprung beim Einschalten), {@code energyKwh}
 * daraus gerechnet – also eine Schätzung. {@code estimated} sagt das ausdrücklich, damit
 * die Oberfläche es kennzeichnen kann und niemand die Zahl für eine Zählermessung hält.
 */
public record ChargingSessionDto(
        String startedAt, String endedAt, long minutes, double watt, double energyKwh, boolean estimated) {

    public static ChargingSessionDto from(ChargingSession session) {
        return new ChargingSessionDto(
                session.startedAt().toString(),
                session.endedAt().toString(),
                session.duration().toMinutes(),
                session.watt(),
                session.energyKwh(),
                true);
    }
}
