package fabianaschwanden.smarthome.support.tuya;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TuyaDiscoveryTest {

    @Test
    void parstVerschluesseltenBroadcast() throws Exception {
        String json = "{\"ip\":\"192.168.113.42\",\"gwId\":\"bf123abc\",\"version\":\"3.3\"}";
        byte[] datagram = encryptedBroadcast(json);

        Optional<TuyaDiscovery.TuyaBroadcast> bc = TuyaDiscovery.parse(datagram);

        assertTrue(bc.isPresent());
        assertEquals("bf123abc", bc.get().deviceId());
        assertEquals("192.168.113.42", bc.get().ip());
    }

    @Test
    void parstKlartextBroadcast() {
        // Port 6666: unverschlüsseltes JSON im 55aa-Rahmen.
        String json = "{\"ip\":\"10.0.0.5\",\"gwId\":\"plain123\"}";
        byte[] datagram = frame(json.getBytes(StandardCharsets.UTF_8));

        Optional<TuyaDiscovery.TuyaBroadcast> bc = TuyaDiscovery.parse(datagram);

        assertTrue(bc.isPresent());
        assertEquals("plain123", bc.get().deviceId());
        assertEquals("10.0.0.5", bc.get().ip());
    }

    @Test
    void leerBeiMuell() {
        assertTrue(TuyaDiscovery.parse(new byte[] {1, 2, 3}).isEmpty());
    }

    private static byte[] encryptedBroadcast(String json) throws Exception {
        byte[] key = MessageDigest.getInstance("MD5").digest("yGAdlopoPVldABfn".getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return frame(cipher.doFinal(json.getBytes(StandardCharsets.UTF_8)));
    }

    /** Baut einen 55aa-Discovery-Rahmen (prefix|seq|cmd|len|payload|crc|suffix). */
    private static byte[] frame(byte[] payload) {
        int length = payload.length + 8;
        ByteBuffer head = ByteBuffer.allocate(20);
        head.put(new byte[] {0x00, 0x00, 0x55, (byte) 0xaa});
        head.putInt(0);
        head.putInt(0);
        head.putInt(length);
        head.putInt(0); // retcode-Platz (parse überspringt header bis 20)
        CRC32 crc = new CRC32();
        crc.update(head.array());
        crc.update(payload);
        ByteBuffer out = ByteBuffer.allocate(20 + payload.length + 8);
        out.put(head.array());
        out.put(payload);
        out.putInt((int) crc.getValue());
        out.put(new byte[] {0x00, 0x00, (byte) 0xaa, 0x55});
        return out.array();
    }
}
