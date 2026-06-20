package fabianaschwanden.smarthome.adapter.out.safety.local;

import fabianaschwanden.smarthome.domain.model.safety.AlarmState;
import fabianaschwanden.smarthome.domain.port.out.safety.SmokeDetectorDevice;
import fabianaschwanden.smarthome.support.tuya.TuyaDiscovery;
import fabianaschwanden.smarthome.support.tuya.TuyaProtocol;
import fabianaschwanden.smarthome.support.tuya.TuyaSidecarClient;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Echter Tuya-Rauchmelder über das lokale LAN-Protokoll (nur lesend, dp_query).
 * dps: statusDp (String "alarm"/"normal"), batteryDp (%). 3.4/3.5 über den
 * tinytuya-Sidecar, 3.3 direkt. IP-Fallback via {@link TuyaDiscovery}.
 */
public class LocalTuyaSmokeDetector implements SmokeDetectorDevice {

    private static final Logger LOG = Logger.getLogger(LocalTuyaSmokeDetector.class);
    private static final int PORT = 6668;
    private static final int TIMEOUT_MS = 4000;
    /**
     * Rauchmelder schlafen (Deep Sleep) und antworten kaum auf aktive dp_query, senden
     * aber periodisch UDP-Broadcasts. Wer innerhalb dieses Fensters einen Broadcast
     * gesendet hat, gilt als erreichbar (Alarm dann OK, Batterie unbekannt).
     */
    private static final Duration SEEN_WINDOW = Duration.ofMinutes(30);

    private final String id;
    private final String name;
    private final String room;
    private final String deviceId;
    private final String localKey;
    private final String configuredAddress;
    private final String version;
    private final boolean v34;
    private final int statusDp;
    private final int batteryDp;
    private final TuyaProtocol protocol33;
    private final TuyaDiscovery discovery;
    private final TuyaSidecarClient sidecar;
    private final AtomicInteger sequence = new AtomicInteger(1);

    public LocalTuyaSmokeDetector(
            String id, String name, String room, String deviceId, String localKey, String address,
            String version, int statusDp, int batteryDp, TuyaDiscovery discovery, TuyaSidecarClient sidecar) {
        this.id = id;
        this.name = name;
        this.room = room;
        this.deviceId = deviceId;
        this.localKey = localKey;
        this.configuredAddress = address;
        this.version = version;
        this.v34 = version != null && (version.startsWith("3.4") || version.startsWith("3.5"));
        this.statusDp = statusDp;
        this.batteryDp = batteryDp;
        this.protocol33 = v34 ? null : new TuyaProtocol(localKey);
        this.discovery = discovery;
        this.sidecar = sidecar;
    }

    private String address() {
        return discovery.ipOf(deviceId).orElse(configuredAddress);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String room() {
        return room;
    }

    @Override
    public Optional<Reading> read() {
        try {
            String payload;
            if (v34) {
                payload = sidecar.readDps(deviceId, localKey, address(), version).orElse(null);
            } else {
                String queryJson = "{\"devId\":\"" + deviceId + "\",\"gwId\":\"" + deviceId + "\"}";
                byte[] response = send(protocol33.encode(
                        TuyaProtocol.DP_QUERY, sequence.getAndIncrement(), queryJson));
                payload = protocol33.decodePayload(response);
            }
            if (payload == null) {
                return recentlySeen();
            }
            Optional<String> status = TuyaProtocol.parseStringDp(payload, statusDp);
            if (status.isEmpty()) {
                return recentlySeen();
            }
            AlarmState alarm = "alarm".equalsIgnoreCase(status.get()) ? AlarmState.ALARM : AlarmState.OK;
            OptionalInt battery = TuyaProtocol.parseIntDp(payload, batteryDp);
            return Optional.of(new Reading(alarm, battery.orElse(-1)));
        } catch (Exception e) {
            LOG.debugf("Rauchmelder '%s' nicht lesbar: %s", name, e.getMessage());
            return recentlySeen();
        }
    }

    /**
     * Fallback, wenn die aktive Abfrage nichts liefert (Gerät schläft): War kürzlich
     * ein UDP-Broadcast zu sehen, gilt der Melder als erreichbar (Alarm OK, Batterie
     * unbekannt). Sonst {@code empty} -> offline.
     */
    private Optional<Reading> recentlySeen() {
        return discovery.lastSeen(deviceId)
                .filter(seen -> Duration.between(seen, Instant.now()).compareTo(SEEN_WINDOW) <= 0)
                .map(seen -> new Reading(AlarmState.OK, -1));
    }

    private byte[] send(byte[] frame) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address(), PORT), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            out.write(frame);
            out.flush();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[1024];
            int read = in.read(buffer);
            if (read <= 0) {
                return new byte[0];
            }
            byte[] response = new byte[read];
            System.arraycopy(buffer, 0, response, 0, read);
            return response;
        }
    }
}
