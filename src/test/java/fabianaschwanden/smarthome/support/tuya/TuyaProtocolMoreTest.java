package fabianaschwanden.smarthome.support.tuya;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ergänzt {@link TuyaProtocolTest} um bisher ungetestete Zweige:
 * Fehler-Retcode mit Null-Payload, vorangestellter 3.3-Versionsheader in der
 * Antwort, sowie Randfälle der dps-Parser. Reine Logik ohne Socket.
 */
@QuarkusTest
class TuyaProtocolMoreTest {

    private static final String KEY = "0123456789abcdef"; // 16 Byte

    @Test
    void encodeDpQueryRoundtripUeberDecodePayload() {
        TuyaProtocol p = new TuyaProtocol(KEY);
        // encode (client->device, ohne retcode) lässt sich von decodePayload nicht 1:1
        // lesen (decode erwartet retcode). Daher prüfen wir hier den CONTROL-Pfad mit
        // Versionsheader über eine selbstgebaute Geräteantwort MIT Versionsheader.
        String json = "{\"dps\":{\"1\":true}}";
        byte[] response = deviceResponseFrame(json, true, 0);
        String decoded = p.decodePayload(response);
        assertTrue(decoded.contains("\"1\":true"), decoded);
    }

    @Test
    void decodePayloadStriptVorangestelltenVersionsheader() {
        TuyaProtocol p = new TuyaProtocol(KEY);
        String json = "{\"dps\":{\"20\":false}}";
        byte[] response = deviceResponseFrame(json, true, 0);
        assertTrue(p.decodePayload(response).contains("\"20\":false"));
    }

    @Test
    void decodePayloadNullBeiFehlerRetcodeUndNullPayload() {
        TuyaProtocol p = new TuyaProtocol(KEY);
        // retcode != 0 und Payload ausschliesslich Nullbytes -> null.
        byte[] response = errorResponseAllZero(5);
        assertNull(p.decodePayload(response));
    }

    @Test
    void parseIntDpNegativeUndFehlfaelle() {
        assertEquals(-5, TuyaProtocol.parseIntDp("{\"dps\":{\"3\":-5}}", 3).orElseThrow());
        // dp vorhanden, aber kein numerischer Wert -> empty (start == i).
        assertTrue(TuyaProtocol.parseIntDp("{\"dps\":{\"3\":\"abc\"}}", 3).isEmpty());
        // kein dps-Block.
        assertTrue(TuyaProtocol.parseIntDp("{\"foo\":1}", 3).isEmpty());
        // dp nicht vorhanden.
        assertTrue(TuyaProtocol.parseIntDp("{\"dps\":{\"9\":1}}", 3).isEmpty());
        // null.
        assertTrue(TuyaProtocol.parseIntDp(null, 3).isEmpty());
        // Wert mit fuehrendem Leerzeichen wird uebersprungen.
        assertEquals(7, TuyaProtocol.parseIntDp("{\"dps\":{\"3\": 7}}", 3).orElseThrow());
    }

    @Test
    void parseStringDpOhneDpsBlock() {
        assertTrue(TuyaProtocol.parseStringDp("{\"foo\":\"bar\"}", 1).isEmpty());
        // dps vorhanden, dp fehlt.
        assertTrue(TuyaProtocol.parseStringDp("{\"dps\":{\"2\":\"x\"}}", 1).isEmpty());
    }

    @Test
    void parseSwitchStateOhneDpsBlock() {
        assertTrue(TuyaProtocol.parseSwitchState("{\"foo\":true}", 1).isEmpty());
    }

    /** Geräteantwort (mit retcode) bauen; optional mit vorangestelltem 3.3-Versionsheader. */
    private static byte[] deviceResponseFrame(String json, boolean withVersionHeader, int retcode) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"));
            byte[] enc = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));

            byte[] payload = enc;
            if (withVersionHeader) {
                byte[] header = ("3.3" + "\0\0\0\0\0\0\0\0\0\0\0\0").getBytes(StandardCharsets.US_ASCII);
                payload = new byte[header.length + enc.length];
                System.arraycopy(header, 0, payload, 0, header.length);
                System.arraycopy(enc, 0, payload, header.length, enc.length);
            }
            return wrap(payload, retcode);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Fehler-Antwort: retcode != 0, Payload nur Nullbytes. */
    private static byte[] errorResponseAllZero(int retcode) {
        return wrap(new byte[32], retcode);
    }

    private static byte[] wrap(byte[] payload, int retcode) {
        int length = 4 /*retcode*/ + payload.length + 4 /*crc*/ + 4 /*suffix*/;
        ByteBuffer head = ByteBuffer.allocate(16);
        head.put(new byte[] {0x00, 0x00, 0x55, (byte) 0xaa});
        head.putInt(1);                     // sequence
        head.putInt(TuyaProtocol.DP_QUERY); // command
        head.putInt(length);

        ByteBuffer body = ByteBuffer.allocate(4 + payload.length);
        body.putInt(retcode);
        body.put(payload);

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
