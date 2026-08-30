package fabianaschwanden.smarthome.adapter.in.rest.dto.charging;

import fabianaschwanden.smarthome.domain.model.charging.ChargingSession;

/**
 * Transport-Objekt eines Ladevorgangs.
 *
 * <p>{@code measuredWatt} ist die Gegenmessung aus der Mitte des Ladevorgangs; sie ist
 * {@code null}, wenn keine stattgefunden hat. Weicht sie stark von {@code watt} ab, hat
 * beim Einschalten vermutlich eine andere Last mitgeschaltet – dann taugt die
 * Gegenmessung mehr.
 *
 * <p>{@code watt} ist gemessen (Verbrauchssprung beim Einschalten), {@code energyKwh}
 * daraus gerechnet – also eine Schätzung. {@code estimated} sagt das ausdrücklich, damit
 * die Oberfläche es kennzeichnen kann und niemand die Zahl für eine Zählermessung hält.
 */
public record ChargingSessionDto(
        String startedAt,
        String endedAt,
        long minutes,
        double watt,
        Double measuredWatt,
        double energyKwh,
        boolean estimated) {

    public static ChargingSessionDto from(ChargingSession session) {
        return new ChargingSessionDto(
                session.startedAt().toString(),
                session.endedAt().toString(),
                session.duration().toMinutes(),
                session.watt(),
                session.measuredWatt().isPresent() ? session.measuredWatt().getAsDouble() : null,
                session.energyKwh(),
                true);
    }
}
