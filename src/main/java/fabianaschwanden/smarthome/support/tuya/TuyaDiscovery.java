package fabianaschwanden.smarthome.support.tuya;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tuya-Geräte broadcasten sich periodisch per UDP (Port 6666 unverschlüsselt,
 * 6667 mit globalem Key) mit ihrer {@code gwId} (device-id) und aktuellen IP.
 * Dieser Hintergrund-Listener pflegt daraus eine {@code device-id -> IP}-Map und
 * dient den LAN-Adaptern als Fallback, wenn die konfigurierte IP gewandert ist
 * (DHCP). Reines Auflösen von Adressen – keine Steuerung.
 *
 * <p>Aktiv nur im Echtbetrieb ({@code smarthome.real-devices=true}); im Mock/Test
 * wird kein Socket geöffnet.
 */
@ApplicationScoped
public class TuyaDiscovery {

    private static final Logger LOG = Logger.getLogger(TuyaDiscovery.class);
    private static final int[] PORTS = {6667, 6666};
    // Global bekannter UDP-Broadcast-Key: md5("yGAdlopoPVldABfn").
    private static final byte[] UDP_KEY = md5("yGAdlopoPVldABfn");

    private final boolean enabled;
    private final Map<String, String> deviceIdToIp = new ConcurrentHashMap<>();
    /** Zeitpunkt der letzten Broadcast-Sichtung je device-id (passiver „Online"-Beleg). */
    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();
    private volatile boolean running;

    public TuyaDiscovery(
            @org.eclipse.microprofile.config.inject.ConfigProperty(
                    name = "smarthome.real-devices", defaultValue = "false") boolean realDevices) {
        this.enabled = realDevices;
    }

    void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            return;
        }
        running = true;
        for (int port : PORTS) {
            Thread.ofVirtual().name("tuya-discovery-" + port).start(() -> listen(port));
        }
        LOG.info("Tuya-Discovery aktiv (UDP 6666/6667)");
    }

    void onStop(@Observes ShutdownEvent event) {
        running = false;
    }

    /** Aktuelle IP eines Geräts, falls per Broadcast gesehen. */
    public Optional<String> ipOf(String deviceId) {
        return Optional.ofNullable(deviceIdToIp.get(deviceId));
    }

    /** Zeitpunkt der letzten Broadcast-Sichtung eines Geräts (passiver „zuletzt online"). */
    public Optional<Instant> lastSeen(String deviceId) {
        return Optional.ofNullable(lastSeen.get(deviceId));
    }

    private void listen(int port) {
        while (running) {
            try (DatagramSocket socket = new DatagramSocket(null)) {
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(port));
                socket.setSoTimeout(2000);
                byte[] buffer = new byte[2048];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        handle(Arrays.copyOf(packet.getData(), packet.getLength()));
                    } catch (java.net.SocketTimeoutException ignored) {
                        // erlaubt das Prüfen der running-Flag
                    }
                }
            } catch (Exception e) {
                LOG.debugf("Discovery auf Port %d: %s", port, e.getMessage());
                sleep();
            }
        }
    }

    private void handle(byte[] datagram) {
        Optional<TuyaBroadcast> bc = parse(datagram);
        if (bc.isEmpty()) {
            return;
        }
        String deviceId = bc.get().deviceId();
        if (bc.get().ip() != null) {
            deviceIdToIp.put(deviceId, bc.get().ip());
        }
        // Passiver „Online"-Beleg: erste Sichtung bzw. Wiederauftauchen nach Stille
        // gut sichtbar loggen (z. B. ein aufwachender Rauchmelder).
        Instant now = Instant.now();
        Instant previous = lastSeen.put(deviceId, now);
        if (previous == null || java.time.Duration.between(previous, now).toMinutes() >= 1) {
            LOG.infof("Tuya-Broadcast gesehen: device-id=%s ip=%s", deviceId, bc.get().ip());
        }
    }

    /**
     * Entschlüsselt/parst einen Broadcast-Datagramm in device-id + IP. Pur und
     * ohne Socket – unit-testbar. Liefert {@code empty} bei nicht interpretierbaren Paketen.
     */
    static Optional<TuyaBroadcast> parse(byte[] datagram) {
        if (datagram.length < 28
                || datagram[0] != 0x00 || datagram[1] != 0x00
                || (datagram[2] & 0xff) != 0x55 || (datagram[3] & 0xff) != 0xaa) {
            return Optional.empty();
        }
        byte[] payload = Arrays.copyOfRange(datagram, 20, datagram.length - 8);
        String json = tryDecrypt(payload);
        if (json == null) {
            // Port 6666: Klartext-JSON (kein AES).
            json = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
        }
        String gwId = field(json, "gwId");
        if (gwId == null) {
            return Optional.empty();
        }
        return Optional.of(new TuyaBroadcast(gwId, field(json, "ip")));
    }

    private static String tryDecrypt(byte[] payload) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(UDP_KEY, "AES"));
            return new String(cipher.doFinal(payload), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String field(String json, String name) {
        if (json == null) {
            return null;
        }
        String marker = "\"" + name + "\":\"";
        int i = json.indexOf(marker);
        if (i < 0) {
            return null;
        }
        int start = i + marker.length();
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
    }

    private static byte[] md5(String s) {
        try {
            return MessageDigest.getInstance("MD5").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void sleep() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Aufgelöste Broadcast-Daten. */
    record TuyaBroadcast(String deviceId, String ip) {
    }
}
