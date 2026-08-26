package fabianaschwanden.smarthome.domain.port.in.charging;

import fabianaschwanden.smarthome.domain.model.charging.ChargingSession;

import java.util.List;

/**
 * Treiber-Port (Use Case): Wie viel Energie ging in die letzten Ladevorgänge?
 *
 * <p>Die Werte sind Schätzungen aus dem Verbrauchssprung – die Anlage misst das
 * Lade-Relais nicht separat.
 */
public interface ChargingSessionQuery {

    List<ChargingSession> recentSessions(int limit);
}
