package fabianaschwanden.smarthome.adapter.out.tuya.local;

import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import fabianaschwanden.smarthome.domain.port.out.tuya.SwitchDevice;
import fabianaschwanden.smarthome.domain.port.out.tuya.SwitchUnavailable;
import fabianaschwanden.smarthome.support.tuya.Tuya34Protocol;
import fabianaschwanden.smarthome.support.tuya.Tuya34Session;
import fabianaschwanden.smarthome.support.tuya.TuyaDiscovery;
import fabianaschwanden.smarthome.support.tuya.TuyaProtocol;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Echter Tuya-Schalter über das lokale LAN-Protokoll (Port 6668). Unterstützt
 * v3.3 (AES-ECB, CRC32, ohne Handshake) und v3.4 (Session-Key-Handshake,
 * HMAC-SHA256) – die Version kommt aus der Konfiguration. Schaltet per
 * {@code CONTROL} und liest den Zustand per {@code DP_QUERY}.
 *
 * <p>Eine Instanz pro konfiguriertem Gerät (erzeugt von der Factory). Fehler beim
 * Schalten werden als {@link SwitchUnavailable} hochgereicht; beim Lesen führt ein
 * Fehler zu {@code empty} (Gerät gilt als offline).
 */
public class LocalTuyaSwitchDevice implements SwitchDevice {

    private static final Logger LOG = Logger.getLogger(LocalTuyaSwitchDevice.class);
    private static final int PORT = 6668;
    private static final int TIMEOUT_MS = 4000;

    private final String id;
    private final String name;
    private final String room;
    private final String deviceId;
    private final String localKey;
    private final String configuredAddress;
    private final String version;
    private final int dp;
    private final boolean critical;
    private final String hint;
    private final boolean v34;
    private final TuyaProtocol protocol33;
    private final TuyaDiscovery discovery;
    private final AtomicInteger sequence = new AtomicInteger(1);

    public LocalTuyaSwitchDevice(
            String id, String name, String room, String deviceId,
            String localKey, String address, String version, int dp, boolean critical, String hint,
            TuyaDiscovery discovery) {
        this.id = id;
        this.name = name;
        this.room = room;
        this.deviceId = deviceId;
        this.localKey = localKey;
        this.configuredAddress = address;
        this.version = version;
        this.dp = dp;
        this.critical = critical;
        this.hint = hint;
        this.v34 = version != null && (version.startsWith("3.4") || version.startsWith("3.5"));
        this.protocol33 = v34 ? null : new TuyaProtocol(localKey);
        this.discovery = discovery;
    }

    /** Effektive IP: per Discovery gefundene (aktuelle) bevorzugt, sonst die konfigurierte. */
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
    public boolean critical() {
        return critical;
    }

    @Override
    public String hint() {
        return hint;
    }

    @Override
    public void apply(SwitchState state) {
        boolean on = state == SwitchState.ON;
        String controlJson = "{\"devId\":\"" + deviceId + "\",\"uid\":\"" + deviceId
                + "\",\"t\":\"" + (System.currentTimeMillis() / 1000)
                + "\",\"dps\":{\"" + dp + "\":" + on + "}}";
        try {
            if (v34) {
                try (Tuya34Session session = Tuya34Session.open(address(), localKey, TIMEOUT_MS)) {
                    session.send(Tuya34Protocol.CONTROL, controlJson);
                }
            } else {
                send(protocol33.encode(TuyaProtocol.CONTROL, sequence.getAndIncrement(), controlJson));
            }
            LOG.infof("Tuya '%s' (%s, v%s) -> %s", name, id, version, state);
        } catch (Exception e) {
            throw new SwitchUnavailable("Tuya '" + name + "' nicht schaltbar: " + e.getMessage());
        }
    }

    @Override
    public Optional<SwitchState> readState() {
        String queryJson = "{\"devId\":\"" + deviceId + "\",\"gwId\":\"" + deviceId + "\"}";
        try {
            String payload;
            if (v34) {
                try (Tuya34Session session = Tuya34Session.open(address(), localKey, TIMEOUT_MS)) {
                    payload = session.send(Tuya34Protocol.DP_QUERY, queryJson);
                }
            } else {
                byte[] response = send(protocol33.encode(
                        TuyaProtocol.DP_QUERY, sequence.getAndIncrement(), queryJson));
                payload = protocol33.decodePayload(response);
            }
            if (payload == null) {
                return Optional.empty();
            }
            return TuyaProtocol.parseSwitchState(payload, dp);
        } catch (Exception e) {
            LOG.debugf("Tuya '%s' Status nicht lesbar: %s", name, e.getMessage());
            return Optional.empty();
        }
    }

    /** v3.3: kurze Verbindung, Frame senden, Antwort lesen. */
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
