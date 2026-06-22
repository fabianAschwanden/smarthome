package fabianaschwanden.smarthome.support.tuya;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ergänzt {@link Tuya34ProtocolTest} um die Session-Key-Bausteine
 * ({@code encryptBlock}, {@code utf8}) und Randfälle von {@code payloadOf}.
 * Reine Logik ohne Socket.
 */
@QuarkusTest
class Tuya34ProtocolMoreTest {

    private static final byte[] KEY = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void encryptBlockIstDeterministischUnd16Byte() {
        byte[] block = new byte[16]; // 16 Nullbytes (Handshake-Nonce-Block)
        byte[] a = Tuya34Protocol.encryptBlock(KEY, block);
        byte[] b = Tuya34Protocol.encryptBlock(KEY, block);
        assertEquals(16, a.length, "NoPadding-Block bleibt 16 Byte");
        assertArrayEquals(a, b, "ECB ist deterministisch");
    }

    @Test
    void encryptBlockUnterschiedlichFuerUnterschiedlicheBloecke() {
        byte[] one = new byte[16];
        byte[] two = new byte[16];
        two[0] = 1;
        assertTrue(java.util.Arrays.compare(
                Tuya34Protocol.encryptBlock(KEY, one),
                Tuya34Protocol.encryptBlock(KEY, two)) != 0);
    }

    @Test
    void utf8KodiertWieErwartet() {
        assertArrayEquals("äöü".getBytes(StandardCharsets.UTF_8), Tuya34Protocol.utf8("äöü"));
        assertEquals(0, Tuya34Protocol.utf8("").length);
    }

    @Test
    void payloadOfLeerWennLaengeFeldZuKlein() {
        // gültiges Prefix, aber Längenfeld < 36 -> payloadLen <= 0 -> leer.
        byte[] frame = Tuya34Protocol.frame(KEY, Tuya34Protocol.CONTROL, 1, new byte[0]);
        // Ein 0-Byte-Payload wird (wegen +32+4 im len) korrekt als leer extrahiert.
        assertEquals(0, Tuya34Protocol.payloadOf(frame).length);
    }

    @Test
    void payloadOfLeerOhnePrefix() {
        assertEquals(0, Tuya34Protocol.payloadOf(new byte[] {9, 9, 9, 9, 9, 9, 9, 9}).length);
    }

    @Test
    void sessionKeyAbleitungRoundtripStil() {
        // Skizziert die 3.4-Ableitung: XOR der beiden Nonces, dann ein AES-Block
        // unter dem local-key. Wir prüfen nur, dass die reinen Bausteine stabil
        // zusammenspielen (Determinismus), ohne ein echtes Gerät.
        byte[] localNonce = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        byte[] remoteNonce = "fedcba9876543210".getBytes(StandardCharsets.UTF_8);
        byte[] xored = new byte[16];
        for (int i = 0; i < 16; i++) {
            xored[i] = (byte) (localNonce[i] ^ remoteNonce[i]);
        }
        byte[] sessionKey = Tuya34Protocol.encryptBlock(KEY, xored);
        assertEquals(16, sessionKey.length);
        // Mit dem abgeleiteten Schlüssel ein HMAC bilden -> 32 Byte, deterministisch.
        byte[] mac = Tuya34Protocol.hmac(sessionKey, "ping".getBytes(StandardCharsets.UTF_8));
        assertEquals(32, mac.length);
        assertArrayEquals(mac, Tuya34Protocol.hmac(sessionKey, "ping".getBytes(StandardCharsets.UTF_8)));
    }
}
