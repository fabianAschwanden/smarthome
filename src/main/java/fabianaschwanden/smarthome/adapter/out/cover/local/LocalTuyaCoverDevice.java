package fabianaschwanden.smarthome.adapter.out.cover.local;

import fabianaschwanden.smarthome.domain.model.cover.CoverCommand;
import fabianaschwanden.smarthome.domain.port.out.cover.CoverDevice;
import fabianaschwanden.smarthome.domain.port.out.cover.CoverUnavailable;
import fabianaschwanden.smarthome.support.tuya.Tuya34Protocol;
import fabianaschwanden.smarthome.support.tuya.Tuya34Session;
import fabianaschwanden.smarthome.support.tuya.TuyaDiscovery;
import fabianaschwanden.smarthome.support.tuya.TuyaProtocol;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Echte Tuya-Store über das lokale LAN-Protokoll (v3.3/v3.4). Standard-Datenpunkte
 * (siehe docs/cover/SPEC.md): controlDp = {@code "open"/"close"/"stop"},
 * positionDp = Soll-Position (0..100), stateDp = Ist-Position. Wiederverwendung der
 * geteilten Protokoll-Klassen aus {@code support.tuya}.
 */
public class LocalTuyaCoverDevice implements CoverDevice {

    private static final Logger LOG = Logger.getLogger(LocalTuyaCoverDevice.class);
    private static final int PORT = 6668;
    private static final int TIMEOUT_MS = 4000;

    private final String id;
    private final String name;
    private final String room;
    private final String deviceId;
    private final String localKey;
    private final String configuredAddress;
    private final String version;
    private final int controlDp;
    private final int positionDp;
    private final int stateDp;
    private final boolean v34;
    private final TuyaProtocol protocol33;
    private final TuyaDiscovery discovery;
    private final AtomicInteger sequence = new AtomicInteger(1);

    public LocalTuyaCoverDevice(
            String id, String name, String room, String deviceId, String localKey,
            String address, String version, int controlDp, int positionDp, int stateDp,
            TuyaDiscovery discovery) {
        this.id = id;
        this.name = name;
        this.room = room;
        this.deviceId = deviceId;
        this.localKey = localKey;
        this.configuredAddress = address;
        this.version = version;
        this.controlDp = controlDp;
        this.positionDp = positionDp;
        this.stateDp = stateDp;
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
    public void apply(CoverCommand command) {
        String value = switch (command) {
            case OPEN -> "open";
            case CLOSE -> "close";
            case STOP -> "stop";
        };
        sendControl("{\"" + controlDp + "\":\"" + value + "\"}");
        LOG.infof("Store '%s' (%s, v%s) -> %s", name, id, version, command);
    }

    @Override
    public void setPosition(int position) {
        sendControl("{\"" + positionDp + "\":" + position + "}");
        LOG.infof("Store '%s' (%s) -> Position %d", name, id, position);
    }

    @Override
    public OptionalInt readPosition() {
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
                return OptionalInt.empty();
            }
            // Ist-Position bevorzugt aus stateDp, sonst aus positionDp.
            OptionalInt state = TuyaProtocol.parseIntDp(payload, stateDp);
            return state.isPresent() ? state : TuyaProtocol.parseIntDp(payload, positionDp);
        } catch (Exception e) {
            LOG.debugf("Store '%s' Position nicht lesbar: %s", name, e.getMessage());
            return OptionalInt.empty();
        }
    }

    private void sendControl(String dps) {
        String controlJson = "{\"devId\":\"" + deviceId + "\",\"uid\":\"" + deviceId
                + "\",\"t\":\"" + (System.currentTimeMillis() / 1000) + "\",\"dps\":" + dps + "}";
        try {
            if (v34) {
                try (Tuya34Session session = Tuya34Session.open(address(), localKey, TIMEOUT_MS)) {
                    session.send(Tuya34Protocol.CONTROL, controlJson);
                }
            } else {
                send(protocol33.encode(TuyaProtocol.CONTROL, sequence.getAndIncrement(), controlJson));
            }
        } catch (Exception e) {
            throw new CoverUnavailable("Store '" + name + "' nicht steuerbar: " + e.getMessage());
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
