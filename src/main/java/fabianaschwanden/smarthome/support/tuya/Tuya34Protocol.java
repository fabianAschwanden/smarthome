package fabianaschwanden.smarthome.support.tuya;

import org.jboss.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Tuya-LAN-Protokoll v3.4 (Frame {@code 55AA}, AES-128-ECB, Integrität per
 * HMAC-SHA256 statt CRC32). Vor Datenbefehlen ist ein Session-Key-Handshake nötig
 * (Befehle 0x03/0x04/0x05): Client- und Geräte-Nonce werden ausgetauscht und der
 * Session-Key abgeleitet; alle weiteren Befehle nutzen diesen Schlüssel.
 *
 * <p>Reine Kodierung/HMAC/AES – kein Socket (siehe {@link Tuya34Session}), daher
 * unit-testbar. Referenz: tinytuya PROTOCOL.md, localtuya.
 */
public final class Tuya34Protocol {

    public static final int SESS_KEY_NEG_START = 0x03;
    public static final int SESS_KEY_NEG_RESP = 0x04;
    public static final int SESS_KEY_NEG_FINISH = 0x05;
    public static final int DP_QUERY = 0x10;     // 3.4: DP_QUERY_NEW
    public static final int CONTROL = 0x0d;      // 3.4: CONTROL_NEW

    private static final Logger LOG = Logger.getLogger(Tuya34Protocol.class);
    private static final byte[] PREFIX = {0x00, 0x00, 0x55, (byte) 0xaa};
    private static final byte[] SUFFIX = {0x00, 0x00, (byte) 0xaa, 0x55};

    private Tuya34Protocol() {
    }

    /** Verschlüsselt {@code data} mit AES-128-ECB (PKCS7) unter {@code key} (16 B). */
    static byte[] encrypt(byte[] key, byte[] data) {
        return aes(Cipher.ENCRYPT_MODE, key, data);
    }

    static byte[] decrypt(byte[] key, byte[] data) {
        return aes(Cipher.DECRYPT_MODE, key, data);
    }

    /** AES-128-ECB ohne Padding (genau ein 16-Byte-Block) – für die Session-Key-Ableitung. */
    static byte[] encryptBlock(byte[] key, byte[] block16) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(block16);
        } catch (Exception e) {
            throw new IllegalStateException("AES-Block (3.4) fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /**
     * Baut einen 3.4-Frame: {@code prefix|seq|cmd|len|payload|hmac(32)|suffix}.
     * Der Payload muss bei Datenbefehlen bereits AES-verschlüsselt sein; das HMAC
     * deckt Header+Payload ab (Schlüssel = local-key im Handshake, sonst session-key).
     */
    static byte[] frame(byte[] hmacKey, int command, int sequence, byte[] payload) {
        ByteBuffer head = ByteBuffer.allocate(16);
        head.put(PREFIX);
        head.putInt(sequence);
        head.putInt(command);
        head.putInt(payload.length + 32 + 4); // len = payload + hmac(32) + suffix(4)

        byte[] headerAndPayload = new byte[16 + payload.length];
        System.arraycopy(head.array(), 0, headerAndPayload, 0, 16);
        System.arraycopy(payload, 0, headerAndPayload, 16, payload.length);
        byte[] digest = hmac(hmacKey, headerAndPayload);

        ByteBuffer out = ByteBuffer.allocate(16 + payload.length + 32 + 4);
        out.put(headerAndPayload);
        out.put(digest);
        out.put(SUFFIX);
        return out.array();
    }

    /**
     * Extrahiert den (noch verschlüsselten) Payload aus einer Geräteantwort und PRÜFT
     * dabei das mitgelieferte HMAC.
     *
     * <p>Das HMAC ist der einzige Authentizitätsnachweis von 3.4 – wird es nur beim
     * Senden gebildet und beim Empfang verworfen, ist es wirkungslos: ein Fake-Gerät
     * (ARP-/Broadcast-Spoofing) könnte beliebige Antworten unterschieben, und die App
     * hielte die Sitzung für echt. Bei Mismatch liefert die Methode einen leeren Payload,
     * die Antwort wird also wie „unlesbar" behandelt.
     */
    static byte[] payloadOf(byte[] hmacKey, byte[] response) {
        int idx = indexOf(response, PREFIX);
        if (idx < 0 || response.length < idx + 16) {
            return new byte[0];
        }
        ByteBuffer buf = ByteBuffer.wrap(response, idx, response.length - idx);
        buf.position(idx + 12);
        int length = buf.getInt();
        int payloadLen = length - 32 - 4; // ohne hmac + suffix
        // 3.4-Antworten haben i.d.R. KEINEN retcode bei Handshake; Datenantworten ggf. schon.
        if (payloadLen <= 0 || buf.remaining() < payloadLen + 32) {
            return new byte[0];
        }
        byte[] payload = new byte[payloadLen];
        buf.get(payload);
        byte[] received = new byte[32];
        buf.get(received);

        // HMAC deckt Header (16 Byte ab PREFIX) + Payload ab – wie beim Senden in frame().
        byte[] headerAndPayload = new byte[16 + payloadLen];
        System.arraycopy(response, idx, headerAndPayload, 0, 16);
        System.arraycopy(payload, 0, headerAndPayload, 16, payloadLen);
        if (!MessageDigest.isEqual(hmac(hmacKey, headerAndPayload), received)) {
            LOG.warnf("Tuya 3.4: HMAC der Antwort stimmt nicht - Frame verworfen (%d Byte)", response.length);
            return new byte[0];
        }
        return payload;
    }

    private static byte[] aes(int mode, byte[] key, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(mode, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("AES (3.4) fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
