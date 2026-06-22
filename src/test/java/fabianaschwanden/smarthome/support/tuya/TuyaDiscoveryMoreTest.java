package fabianaschwanden.smarthome.support.tuya;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ergänzt {@link TuyaDiscoveryTest} um die fehlenden parse-Zweige:
 * fehlende gwId, fehlendes ip-Feld, falsches Prefix. Reine Logik, kein Socket.
 */
@QuarkusTest
class TuyaDiscoveryMoreTest {

    @Test
    void leerWennGwIdFehlt() {
        // Klartext-JSON ohne gwId -> empty.
        byte[] datagram = frame("{\"ip\":\"10.0.0.9\"}".getBytes(StandardCharsets.UTF_8));
        assertTrue(TuyaDiscovery.parse(datagram).isEmpty());
    }

    @Test
    void ipNullWennFeldFehlt() {
        byte[] datagram = frame("{\"gwId\":\"only-id\"}".getBytes(StandardCharsets.UTF_8));
        Optional<TuyaDiscovery.TuyaBroadcast> bc = TuyaDiscovery.parse(datagram);
        assertTrue(bc.isPresent());
        assertEquals("only-id", bc.get().deviceId());
        assertNull(bc.get().ip());
    }

    @Test
    void leerBeiFalschemPrefix() {
        // genug lang (>=28), aber Prefix-Bytes stimmen nicht.
        byte[] datagram = new byte[40];
        datagram[0] = 0x11;
        datagram[1] = 0x22;
        assertTrue(TuyaDiscovery.parse(datagram).isEmpty());
    }

    /** Baut einen 55aa-Discovery-Rahmen (prefix|seq|cmd|len|retcode|payload|crc|suffix). */
    private static byte[] frame(byte[] payload) {
        int length = payload.length + 8;
        ByteBuffer head = ByteBuffer.allocate(20);
        head.put(new byte[] {0x00, 0x00, 0x55, (byte) 0xaa});
        head.putInt(0);
        head.putInt(0);
        head.putInt(length);
        head.putInt(0);
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
