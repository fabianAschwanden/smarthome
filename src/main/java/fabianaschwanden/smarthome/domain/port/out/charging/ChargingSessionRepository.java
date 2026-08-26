package fabianaschwanden.smarthome.domain.port.out.charging;

import fabianaschwanden.smarthome.domain.model.charging.ChargingSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Getriebener Port: die Ladevorgänge der Batterie.
 *
 * <p>Ein laufender Vorgang wird beim Einschalten <b>sofort</b> festgehalten und erst beim
 * Ausschalten vervollständigt. Läge der Beginn nur im Speicher, verschluckte jeder
 * Neustart – und Deploys sind häufig – den ganzen Ladevorgang.
 */
public interface ChargingSessionRepository {

    /** Hält den Beginn fest; ein bereits offener Vorgang bleibt unangetastet. */
    void open(Instant startedAt);

    /** Beginn des laufenden Vorgangs, falls einer offen ist. */
    Optional<Instant> openStart();

    /** Vervollständigt den offenen Vorgang. Ohne offenen Vorgang passiert nichts. */
    void close(ChargingSession session);

    /** Verwirft den offenen Vorgang (wenn sich nichts schätzen liess). */
    void discardOpen();

    /** Die letzten abgeschlossenen Vorgänge, neuester zuerst. */
    List<ChargingSession> latest(int limit);
}
