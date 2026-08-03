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
import java.time.Duration;
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

    /**
     * Verfallszeit der gelernten Adressen. Broadcasts sind UNAUTHENTIFIZIERT – jeder im
     * LAN kann ein Paket mit fremder {@code gwId} und beliebiger IP schicken. Ohne
     * Verfall bliebe so ein Eintrag für immer stehen; mit Verfall muss ein Angreifer das
     * echte Gerät dauerhaft überbieten (das sich selbst alle paar Sekunden meldet).
     */
    private static final Duration ENTRY_TTL = Duration.ofMinutes(10);

    private final boolean enabled;
    private final Map<String, Seen> deviceIdToIp = new ConcurrentHashMap<>();
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

    /** Per Broadcast gesehene IP eines Geräts, sofern nicht älter als {@link #ENTRY_TTL}. */
    public Optional<String> ipOf(String deviceId) {
        Seen seen = deviceIdToIp.get(deviceId);
        if (seen == null || Duration.between(seen.at(), Instant.now()).compareTo(ENTRY_TTL) > 0) {
            return Optional.empty();
        }
        return Optional.of(seen.ip());
    }

    /**
     * Effektive Adresse eines Geräts – die zentrale Vertrauensregel der Discovery.
     *
     * <p>Die KONFIGURIERTE Adresse gewinnt. Ein unauthentifizierter Broadcast darf eine
     * bewusst gesetzte Adresse nicht überschreiben: ein einziges gefälschtes Paket
     * ({@code gwId=<opfer>, ip=<angreifer>}) lenkte sonst das Gerät dauerhaft auf eine
     * fremde Maschine um – Steuerbefehle liefen ins Leere, während die App Erfolg meldet.
     * Die Discovery füllt nur die Lücke, wenn nichts konfiguriert ist.
     *
     * <p>Weicht die gesehene IP von der konfigurierten ab, wird das geloggt: entweder ist
     * das Gerät per DHCP gewandert (dann gehört die neue IP in die Config) – oder jemand
     * fälscht Broadcasts.
     */
    public String resolveAddress(String deviceId, String configuredAddress) {
        boolean configured = configuredAddress != null && !configuredAddress.isBlank()
                && !"0.0.0.0".equals(configuredAddress);
        if (!configured) {
            return ipOf(deviceId).orElse(configuredAddress);
        }
        ipOf(deviceId)
                .filter(seen -> !seen.equals(configuredAddress))
                .ifPresent(seen -> LOG.warnf(
                        "Tuya-Broadcast meldet für device-id=%s die IP %s, konfiguriert ist %s."
                                + " Es gilt die konfigurierte Adresse. Bei DHCP-Wechsel Config anpassen;"
                                + " sonst faelscht jemand Broadcasts.",
                        deviceId, seen, configuredAddress));
        return configuredAddress;
    }

    /**
     * Zeitpunkt der letzten Broadcast-Sichtung eines Geräts (passiver „zuletzt online").
     *
     * <p>ACHTUNG: unauthentifizierter Hinweis. Broadcasts sind nicht signiert, jeder im
     * LAN kann sie mit fremder {@code gwId} erzeugen. Nur als weiche Zusatzinfo verwenden,
     * nie als alleinigen Beleg dafür, dass ein Gerät funktioniert.
     */
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
        Instant seenAt = Instant.now();
        if (isIpv4(bc.get().ip())) {
            deviceIdToIp.put(deviceId, new Seen(bc.get().ip(), seenAt));
        }
        // Passiver „Online"-Beleg: erste Sichtung bzw. Wiederauftauchen nach Stille
        // gut sichtbar loggen (z. B. ein aufwachender Rauchmelder).
        Instant now = seenAt;
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

    /**
     * Akzeptiert nur syntaktisch gültige IPv4-Adressen aus dem Datagramm. Das {@code ip}-Feld
     * ist frei wählbarer Text aus dem Netz – ohne Prüfung landete er unbesehen als
     * Verbindungsziel in der Map.
     */
    static boolean isIpv4(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (part.charAt(i) < '0' || part.charAt(i) > '9') {
                    return false;
                }
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
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

    /** Gelernte Adresse mit Sichtungszeitpunkt (für {@link #ENTRY_TTL}). */
    private record Seen(String ip, Instant at) {
    }
}
