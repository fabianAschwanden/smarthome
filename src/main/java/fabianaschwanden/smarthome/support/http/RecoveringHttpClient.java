package fabianaschwanden.smarthome.support.http;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Ein {@link HttpClient}, der sich erneuert, wenn der alte nicht mehr benutzbar ist.
 *
 * <p><b>Warum es das braucht:</b> Ein lange gehaltener {@code HttpClient} kann seinen
 * Selector-Manager verlieren; jeder weitere Aufruf scheitert danach dauerhaft mit
 * {@code IOException: selector manager closed}. Genau das ist am 16.08.2026 dem
 * Relais-Adapter passiert – und weil derselbe Client auch den Ist-Zustand liest, fielen
 * <em>Schalten und Anzeige gleichzeitig</em> aus: Die App meldete «Aus», während das
 * Relais eingeschaltet war, und nahm keinen Befehl mehr an. Erst ein Neustart half.
 *
 * <p>Deshalb wird vor jedem Aufruf geprüft, ob der Client beendet ist, und im Fehlerfall
 * <b>einmal</b> mit einem frischen Client wiederholt. Alle Aufrufe hier sind idempotent
 * (Zustand lesen, einen absoluten Schaltzustand setzen) – eine Wiederholung kann also
 * nichts doppelt auslösen.
 */
public final class RecoveringHttpClient {

    private static final Logger LOG = Logger.getLogger(RecoveringHttpClient.class);

    private final Duration connectTimeout;
    private volatile HttpClient client;

    public RecoveringHttpClient(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        this.client = build();
    }

    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        HttpClient current = current();
        try {
            return current.send(request, handler);
        } catch (IOException e) {
            if (!isUnusable(e)) {
                throw e;
            }
            LOG.warnf("HTTP-Client war unbrauchbar (%s) – neuer Client, ein Wiederholungsversuch",
                    e.getMessage());
            return renew(current).send(request, handler);
        }
    }

    /** Ein beendeter Client nimmt nichts mehr an – dann gar nicht erst versuchen. */
    private HttpClient current() {
        HttpClient existing = client;
        return existing.isTerminated() ? renew(existing) : existing;
    }

    /**
     * Ersetzt genau den Client, der sich als unbrauchbar erwiesen hat.
     *
     * <p>Der Vergleich verhindert, dass mehrere Aufrufer gleichzeitig je einen neuen
     * Client bauen: Wer als Zweiter kommt, findet den bereits erneuerten vor.
     */
    private synchronized HttpClient renew(HttpClient broken) {
        if (client == broken) {
            client = build();
        }
        return client;
    }

    private HttpClient build() {
        return HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    /**
     * Ist der Fehler dem toten Client zuzuschreiben? Die Meldung ist der einzige
     * Anhaltspunkt, den die JDK-Ausnahme liefert – ein eigener Ausnahmetyp existiert
     * nicht.
     */
    private static boolean isUnusable(IOException e) {
        String message = e.getMessage();
        return message != null && message.contains("selector manager closed");
    }
}
