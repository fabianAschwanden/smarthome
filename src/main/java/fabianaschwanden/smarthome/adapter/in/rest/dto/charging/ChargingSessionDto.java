package fabianaschwanden.smarthome.adapter.in.rest.dto.charging;

import fabianaschwanden.smarthome.domain.model.charging.ChargingSession;

/**
 * Transport-Objekt eines Ladevorgangs.
 *
 * <p>{@code verifiedWatt} ist die Gegenmessung aus der Mitte des Ladevorgangs; sie ist
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
        Double verifiedWatt,
        double energyKwh,
        boolean estimated) {

    public static ChargingSessionDto from(ChargingSession session) {
        return new ChargingSessionDto(
                session.startedAt().toString(),
                session.endedAt().toString(),
                session.duration().toMinutes(),
                session.watt(),
                session.verifiedWatt().isPresent() ? session.verifiedWatt().getAsDouble() : null,
                session.energyKwh(),
                true);
    }
}
