package fabianaschwanden.smarthome.adapter.out.sensor.local;

import fabianaschwanden.smarthome.domain.port.out.sensor.SensorDevice;
import fabianaschwanden.smarthome.support.tuya.TuyaDiscovery;
import fabianaschwanden.smarthome.support.tuya.TuyaProtocol;
import fabianaschwanden.smarthome.support.tuya.TuyaSidecarClient;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Echter Tuya-Umweltsensor über das lokale LAN-Protokoll (nur lesend, dp_query).
 * dps: temperatureDp (Rohwert ÷ temperatureScale = °C), humidityDp (%). IP-Fallback
 * via {@link TuyaDiscovery}. Wiederverwendung der geteilten {@code support.tuya}-Klassen.
 *
 * <p>{@link #read()} blockiert nie: der letzte Wert kommt aus dem Cache, erneuert wird
 * asynchron im Hintergrund. Sonst würde ein schlafender Batteriesensor (bis {@code TIMEOUT_MS}
 * ohne Antwort) jede {@code /api/sensors}-Abfrage — inkl. der anderen Sensoren — ausbremsen.
 */
public class LocalTuyaSensorDevice implements SensorDevice {

    private static final Logger LOG = Logger.getLogger(LocalTuyaSensorDevice.class);
    private static final int PORT = 6668;
    private static final int TIMEOUT_MS = 4000;
    /** Frühestens nach dieser Zeit wird im Hintergrund ein neuer Read angestoßen. */
    private static final long CACHE_TTL_MS = 10_000;
    /** So lange kein erfolgreicher Read -> Sensor gilt als offline (Batteriesensor schläft). */
    private static final long OFFLINE_AFTER_MS = 1_800_000;

    private final String id;
    private final String name;
    private final String room;
    private final String deviceId;
    private final String localKey;
    private final String configuredAddress;
    private final boolean v34;
    private final int temperatureDp;
    private final int humidityDp;
    private final int temperatureScale;
    private final String version;
    private final TuyaProtocol protocol33;
    private final TuyaDiscovery discovery;
    private final TuyaSidecarClient sidecar;
    private final AtomicInteger sequence = new AtomicInteger(1);

    private volatile Reading cachedState;
    private volatile long cachedAtMillis;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private final LongSupplier nowMillis;

    public LocalTuyaSensorDevice(
            String id, String name, String room, String deviceId, String localKey, String address,
            String version, int temperatureDp, int humidityDp, int temperatureScale,
            TuyaDiscovery discovery, TuyaSidecarClient sidecar) {
        this(id, name, room, deviceId, localKey, address, version, temperatureDp, humidityDp,
                temperatureScale, discovery, sidecar, System::currentTimeMillis);
    }

    // Sichtbar fürs Testen: injizierbare Zeitquelle (Millis) für die Offline-Schwelle.
    LocalTuyaSensorDevice(
            String id, String name, String room, String deviceId, String localKey, String address,
            String version, int temperatureDp, int humidityDp, int temperatureScale,
            TuyaDiscovery discovery, TuyaSidecarClient sidecar, LongSupplier nowMillis) {
        this.id = id;
        this.name = name;
        this.room = room;
        this.deviceId = deviceId;
        this.localKey = localKey;
        this.configuredAddress = address;
        this.version = version;
        this.v34 = version != null && (version.startsWith("3.4") || version.startsWith("3.5"));
        this.temperatureDp = temperatureDp;
        this.humidityDp = humidityDp;
        this.temperatureScale = temperatureScale <= 0 ? 1 : temperatureScale;
        this.protocol33 = v34 ? null : new TuyaProtocol(localKey);
        this.discovery = discovery;
        this.sidecar = sidecar;
        this.nowMillis = nowMillis;
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
        Reading cached = cachedState;
        long age = nowMillis.getAsLong() - cachedAtMillis;
        if (cached == null || age >= CACHE_TTL_MS) {
            triggerBackgroundRefresh();  // nie blockierend; Cache wird asynchron erneuert
        }
        // Zu lange kein erfolgreicher Read -> Sensor gilt als offline. Ein fehlgeschlagener/
        // leerer Read überschreibt den Cache nicht, sonst würde der letzte Wert ewig gemeldet.
        if (cached == null || age > OFFLINE_AFTER_MS) {
            return Optional.empty();
        }
        return Optional.of(cached);
    }

    /** Liest den Sensor im Hintergrund (höchstens ein Refresh gleichzeitig) und füllt den Cache. */
    private void triggerBackgroundRefresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        Thread.ofVirtual().name("sensor-refresh-" + id).start(() -> {
            try {
                fetch().ifPresent(this::cache);
            } finally {
                refreshing.set(false);
            }
        });
    }

    private void cache(Reading reading) {
        this.cachedState = reading;
        this.cachedAtMillis = nowMillis.getAsLong();
    }

    /** Blockierender Ein-Schuss-Read vom Gerät (Java 3.3 direkt bzw. Sidecar für 3.4/3.5). */
    private Optional<Reading> fetch() {
        String queryJson = "{\"devId\":\"" + deviceId + "\",\"gwId\":\"" + deviceId + "\"}";
        try {
            String payload;
            if (v34) {
                // 3.4/3.5: über den tinytuya-Sidecar (Java-Handshake noch nicht erprobt).
                payload = sidecar.readDps(deviceId, localKey, address(), version).orElse(null);
            } else {
                byte[] response = send(protocol33.encode(
                        TuyaProtocol.DP_QUERY, sequence.getAndIncrement(), queryJson));
                payload = protocol33.decodePayload(response);
            }
            if (payload == null) {
                return Optional.empty();
            }
            OptionalInt rawTemp = TuyaProtocol.parseIntDp(payload, temperatureDp);
            OptionalInt humidity = TuyaProtocol.parseIntDp(payload, humidityDp);
            if (rawTemp.isEmpty()) {
                return Optional.empty();
            }
            double temp = rawTemp.getAsInt() / (double) temperatureScale;
            return Optional.of(new Reading(temp, humidity.orElse(-1)));
        } catch (Exception e) {
            LOG.debugf("Sensor '%s' nicht lesbar: %s", name, e.getMessage());
            return Optional.empty();
        }
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
