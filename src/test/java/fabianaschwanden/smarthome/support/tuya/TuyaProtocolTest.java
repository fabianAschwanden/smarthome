package fabianaschwanden.smarthome.support.tuya;

import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TuyaProtocolTest {

    private static final String KEY = "0123456789abcdef"; // 16 Byte

    @Test
    void lehntFalscheKeylaengeAb() {
        assertThrows(IllegalArgumentException.class, () -> new TuyaProtocol("zu-kurz"));
    }

    @Test
    void controlFrameHatPrefixSuffixUndVersionsheader() {
        TuyaProtocol p = new TuyaProtocol(KEY);
        byte[] frame = p.encode(TuyaProtocol.CONTROL, 1, "{\"dps\":{\"1\":true}}");

        // Prefix 000055aa, Suffix 0000aa55.
        assertEquals(0x00, frame[0]);
        assertEquals(0x55, frame[2] & 0xff);
        assertEquals((byte) 0xaa, frame[3]);
        assertEquals((byte) 0xaa, frame[frame.length - 2]);
        assertEquals(0x55, frame[frame.length - 1] & 0xff);

        // CONTROL trägt den Klartext-Versionsheader "3.3" direkt nach dem 16-Byte-Header.
        String header = new String(frame, 16, 3, StandardCharsets.US_ASCII);
        assertEquals("3.3", header);
    }

    @Test
    void dpQueryFrameOhneVersionsheader() {
        TuyaProtocol p = new TuyaProtocol(KEY);
        byte[] frame = p.encode(TuyaProtocol.DP_QUERY, 2, "{\"gwId\":\"x\"}");

        // DP_QUERY trägt KEINEN Versionsheader -> direkt nach dem 16-Byte-Header
        // beginnt der AES-Ciphertext (kein "3.3").
        String afterHeader = new String(frame, 16, 3, StandardCharsets.US_ASCII);
        assertNotEquals("3.3", afterHeader);
        // Frame-Länge ist Vielfaches-konform: Prefix..Suffix vorhanden.
        assertEquals((byte) 0xaa, frame[frame.length - 2]);
    }

    @Test
    void sequenzUndCommandStehenImFrame() {
        TuyaProtocol p = new TuyaProtocol(KEY);
        byte[] frame = p.encode(TuyaProtocol.CONTROL, 42, "{\"dps\":{\"1\":false}}");
        ByteBuffer buf = ByteBuffer.wrap(frame);
        buf.getInt();                          // prefix
        assertEquals(42, buf.getInt());        // sequence
        assertEquals(TuyaProtocol.CONTROL, buf.getInt()); // command
    }

    @Test
    void decodePayloadLiestVerschluesselteGeraeteantwort() throws Exception {
        TuyaProtocol p = new TuyaProtocol(KEY);
        String json = "{\"dps\":{\"1\":true}}";

        byte[] response = deviceResponseFrame(json);
        String decoded = p.decodePayload(response);

        assertTrue(decoded.contains("\"1\":true"), "entschlüsselte Nutzlast: " + decoded);
    }

    @Test
    void decodePayloadGibtNullBeiMuell() {
        TuyaProtocol p = new TuyaProtocol(KEY);
        assertNull(p.decodePayload(new byte[] {1, 2, 3}));
        assertNull(p.decodePayload(new byte[0]));
    }

    @Test
    void parseSwitchStateLiestDenRichtigenDataPoint() {
        assertEquals(SwitchState.ON,
                TuyaProtocol.parseSwitchState("{\"dps\":{\"1\":true}}", 1).orElseThrow());
        assertEquals(SwitchState.OFF,
                TuyaProtocol.parseSwitchState("{\"dps\":{\"1\":false,\"9\":true}}", 1).orElseThrow());
        assertEquals(SwitchState.ON,
                TuyaProtocol.parseSwitchState("{\"dps\":{\"1\":false,\"20\":true}}", 20).orElseThrow());
    }

    @Test
    void parseSwitchStateLeerOhneTreffer() {
        assertTrue(TuyaProtocol.parseSwitchState("{\"foo\":1}", 1).isEmpty());
        assertTrue(TuyaProtocol.parseSwitchState("{\"dps\":{\"2\":true}}", 1).isEmpty());
        assertTrue(TuyaProtocol.parseSwitchState(null, 1).isEmpty());
    }

    @Test
    void parseIntDpUndStringDp() {
        assertEquals(52, TuyaProtocol.parseIntDp("{\"dps\":{\"10\":52}}", 10).orElseThrow());
        assertEquals(100, TuyaProtocol.parseIntDp("{\"dps\":{\"1\":\"alarm\",\"15\":100}}", 15).orElseThrow());
        assertEquals("alarm",
                TuyaProtocol.parseStringDp("{\"dps\":{\"1\":\"alarm\",\"15\":100}}", 1).orElseThrow());
        assertEquals("normal",
                TuyaProtocol.parseStringDp("{\"dps\":{\"1\":\"normal\"}}", 1).orElseThrow());
        assertTrue(TuyaProtocol.parseStringDp("{\"dps\":{\"2\":51}}", 1).isEmpty());
        assertTrue(TuyaProtocol.parseStringDp(null, 1).isEmpty());
    }

    /** Baut eine Geräteantwort (DP_QUERY-Format: mit retcode, AES-Payload, ohne Versionsheader). */
    private static byte[] deviceResponseFrame(String json) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"));
        byte[] enc = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));

        int length = 4 /*retcode*/ + enc.length + 4 /*crc*/ + 4 /*suffix*/;
        ByteBuffer head = ByteBuffer.allocate(16);
        head.put(new byte[] {0x00, 0x00, 0x55, (byte) 0xaa});
        head.putInt(1);                        // sequence
        head.putInt(TuyaProtocol.DP_QUERY);    // command
        head.putInt(length);

        ByteBuffer body = ByteBuffer.allocate(4 + enc.length);
        body.putInt(0);                        // retcode = 0 (Erfolg)
        body.put(enc);

        CRC32 crc = new CRC32();
        crc.update(head.array());
        crc.update(body.array());

        ByteBuffer out = ByteBuffer.allocate(16 + body.capacity() + 8);
        out.put(head.array());
        out.put(body.array());
        out.putInt((int) crc.getValue());
        out.put(new byte[] {0x00, 0x00, (byte) 0xaa, 0x55});
        return out.array();
    }
}
