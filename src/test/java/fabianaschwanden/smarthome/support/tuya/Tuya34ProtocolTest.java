package fabianaschwanden.smarthome.support.tuya;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class Tuya34ProtocolTest {

    private static final byte[] KEY = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void aesRoundtrip() {
        byte[] data = "{\"dps\":{\"1\":true}}".getBytes(StandardCharsets.UTF_8);
        byte[] enc = Tuya34Protocol.encrypt(KEY, data);
        byte[] dec = Tuya34Protocol.decrypt(KEY, enc);
        assertArrayEquals(data, dec);
    }

    @Test
    void hmacIstDeterministischUnd32Byte() {
        byte[] a = Tuya34Protocol.hmac(KEY, "x".getBytes(StandardCharsets.UTF_8));
        byte[] b = Tuya34Protocol.hmac(KEY, "x".getBytes(StandardCharsets.UTF_8));
        assertEquals(32, a.length);
        assertArrayEquals(a, b);
    }

    @Test
    void frameHatPrefixSuffixUndKorrektenPayload() {
        byte[] payload = Tuya34Protocol.encrypt(KEY, "hallo".getBytes(StandardCharsets.UTF_8));
        byte[] frame = Tuya34Protocol.frame(KEY, Tuya34Protocol.CONTROL, 1, payload);

        // Prefix 000055aa / Suffix 0000aa55.
        assertEquals(0x55, frame[2] & 0xff);
        assertEquals((byte) 0xaa, frame[3]);
        assertEquals((byte) 0xaa, frame[frame.length - 2]);

        // payloadOf liefert den eingebetteten (verschlüsselten) Payload zurück.
        byte[] extracted = Tuya34Protocol.payloadOf(KEY, frame);
        assertArrayEquals(payload, extracted);
    }

    @Test
    void payloadKannEntschluesseltWerden() {
        byte[] payload = Tuya34Protocol.encrypt(KEY, "{\"dps\":{\"1\":false}}".getBytes(StandardCharsets.UTF_8));
        byte[] frame = Tuya34Protocol.frame(KEY, Tuya34Protocol.DP_QUERY, 2, payload);

        byte[] extracted = Tuya34Protocol.payloadOf(KEY, frame);
        String json = new String(Tuya34Protocol.decrypt(KEY, extracted), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"1\":false"), json);
    }

    @Test
    void payloadOfVerwirftManipuliertenFrame() {
        // M4: das HMAC ist der einzige Authentizitaetsnachweis von 3.4 - wird es beim
        // Empfang nicht geprueft, kann ein Fake-Geraet beliebige Antworten unterschieben.
        byte[] payload = Tuya34Protocol.encrypt(KEY, "hallo".getBytes(StandardCharsets.UTF_8));
        byte[] frame = Tuya34Protocol.frame(KEY, Tuya34Protocol.CONTROL, 1, payload);
        frame[20] ^= 0x01; // ein Payload-Bit kippen
        assertEquals(0, Tuya34Protocol.payloadOf(KEY, frame).length);
    }

    @Test
    void payloadOfVerwirftFrameMitFremdemSchluessel() {
        byte[] payload = Tuya34Protocol.encrypt(KEY, "hallo".getBytes(StandardCharsets.UTF_8));
        byte[] fremd = "ZZZZZZZZZZZZZZZZ".getBytes(StandardCharsets.UTF_8);
        byte[] frame = Tuya34Protocol.frame(fremd, Tuya34Protocol.CONTROL, 1, payload);
        assertEquals(0, Tuya34Protocol.payloadOf(KEY, frame).length);
    }

    @Test
    void payloadOfLeerBeiMuell() {
        assertEquals(0, Tuya34Protocol.payloadOf(KEY, new byte[] {1, 2, 3}).length);
    }
}
