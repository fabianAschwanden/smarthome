package fabianaschwanden.smarthome.support.tuya;

import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.CRC32;

/**
 * Tuya-LAN-Protokoll v3.3 (Frame {@code 55AA}, AES-128-ECB mit dem local-key).
 * Reine Kodierung/Dekodierung – kein Socket, damit unit-testbar.
 *
 * <p>Frame: {@code 000055aa | seq(4) | cmd(4) | len(4) | [retcode(4)] | payload | crc32(4) | 0000aa55}.
 * Bei {@code CONTROL} (cmd 7) wird dem AES-Ciphertext der Klartext-Header
 * {@code "3.3" + 12×0x00} vorangestellt; bei {@code DP_QUERY} (cmd 10) nicht.
 * Referenz: tinytuya PROTOCOL.md.
 */
public final class TuyaProtocol {

    public static final int CONTROL = 7;
    public static final int DP_QUERY = 0x0a;

    private static final byte[] PREFIX = {0x00, 0x00, 0x55, (byte) 0xaa};
    private static final byte[] SUFFIX = {0x00, 0x00, (byte) 0xaa, 0x55};
    private static final byte[] VERSION_HEADER_33 =
            ("3.3" + "\0\0\0\0\0\0\0\0\0\0\0\0").getBytes(StandardCharsets.US_ASCII);

    private final SecretKeySpec key;

    public TuyaProtocol(String localKey) {
        byte[] raw = localKey.getBytes(StandardCharsets.UTF_8);
        if (raw.length != 16) {
            throw new IllegalArgumentException("local-key muss 16 Byte lang sein, war " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /** Baut einen verschlüsselten Frame für ein Kommando. */
    public byte[] encode(int command, int sequence, String json) {
        byte[] cipher = aes(Cipher.ENCRYPT_MODE, json.getBytes(StandardCharsets.UTF_8));
        byte[] payload = command == CONTROL ? concat(VERSION_HEADER_33, cipher) : cipher;
        return frame(command, sequence, payload);
    }

    /**
     * Entschlüsselt die JSON-Nutzlast aus einer Geräteantwort. Liefert {@code null},
     * wenn die Antwort keine (entschlüsselbare) Nutzlast enthält.
     */
    public String decodePayload(byte[] response) {
        int idx = indexOf(response, PREFIX);
        if (idx < 0 || response.length < idx + 20) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.wrap(response, idx, response.length - idx);
        buf.position(idx + 4); // prefix
        buf.getInt();          // sequence
        buf.getInt();          // command
        int length = buf.getInt();
        int returnCode = buf.getInt(); // device->client: retcode vorhanden
        // payload = length - retcode(4) - crc(4) - suffix(4); retcode bereits gelesen
        int payloadLen = length - 4 - 4 - 4;
        if (payloadLen <= 0 || buf.remaining() < payloadLen) {
            return null;
        }
        byte[] payload = new byte[payloadLen];
        buf.get(payload);
        if (returnCode != 0 && allZero(payload)) {
            return null;
        }
        // Manche Antworten haben den 3.3-Versions-Header vorangestellt -> abschneiden.
        byte[] cipher = stripVersionHeader(payload);
        byte[] plain = aes(Cipher.DECRYPT_MODE, cipher);
        return new String(plain, StandardCharsets.UTF_8);
    }

    /**
     * Liest den Schaltzustand des Data-Points {@code dp} aus der JSON-Nutzlast
     * ({@code "dps":{"1":true}}). Pur und ohne Geräte-I/O – daher unit-testbar.
     */
    public static Optional<SwitchState> parseSwitchState(String json, int dp) {
        if (json == null) {
            return Optional.empty();
        }
        String marker = "\"" + dp + "\":";
        int dps = json.indexOf("\"dps\"");
        if (dps < 0) {
            return Optional.empty();
        }
        int at = json.indexOf(marker, dps);
        if (at < 0) {
            return Optional.empty();
        }
        String rest = json.substring(at + marker.length()).trim();
        if (rest.startsWith("true")) {
            return Optional.of(SwitchState.ON);
        }
        if (rest.startsWith("false")) {
            return Optional.of(SwitchState.OFF);
        }
        return Optional.empty();
    }

    /**
     * Liest einen numerischen Data-Point aus der JSON-Nutzlast ({@code "dps":{"2":75}}).
     * Für Storen-Positionen (percent). Pur und ohne Geräte-I/O.
     */
    public static java.util.OptionalInt parseIntDp(String json, int dp) {
        if (json == null) {
            return java.util.OptionalInt.empty();
        }
        int dps = json.indexOf("\"dps\"");
        if (dps < 0) {
            return java.util.OptionalInt.empty();
        }
        String marker = "\"" + dp + "\":";
        int at = json.indexOf(marker, dps);
        if (at < 0) {
            return java.util.OptionalInt.empty();
        }
        int i = at + marker.length();
        while (i < json.length() && json.charAt(i) == ' ') {
            i++;
        }
        int start = i;
        while (i < json.length() && (Character.isDigit(json.charAt(i)) || json.charAt(i) == '-')) {
            i++;
        }
        if (i == start) {
            return java.util.OptionalInt.empty();
        }
        try {
            return java.util.OptionalInt.of(Integer.parseInt(json.substring(start, i)));
        } catch (NumberFormatException e) {
            return java.util.OptionalInt.empty();
        }
    }

    /**
     * Liest einen String-Data-Point aus der JSON-Nutzlast ({@code "dps":{"1":"alarm"}}).
     * Für Enum-dps wie Rauchmelder-Status. Pur und ohne Geräte-I/O.
     */
    public static Optional<String> parseStringDp(String json, int dp) {
        if (json == null) {
            return Optional.empty();
        }
        int dps = json.indexOf("\"dps\"");
        if (dps < 0) {
            return Optional.empty();
        }
        String marker = "\"" + dp + "\":\"";
        int at = json.indexOf(marker, dps);
        if (at < 0) {
            return Optional.empty();
        }
        int start = at + marker.length();
        int end = json.indexOf('"', start);
        return end < 0 ? Optional.empty() : Optional.of(json.substring(start, end));
    }

    private byte[] frame(int command, int sequence, byte[] payload) {
        // len zählt ab retcode/payload bis inkl. suffix: hier client->device ohne retcode,
        // also payload + crc(4) + suffix(4).
        int length = payload.length + 4 + 4;
        ByteBuffer head = ByteBuffer.allocate(16);
        head.put(PREFIX);
        head.putInt(sequence);
        head.putInt(command);
        head.putInt(length);

        ByteArrayOutputStream forCrc = new ByteArrayOutputStream();
        forCrc.writeBytes(head.array());
        forCrc.writeBytes(payload);
        CRC32 crc = new CRC32();
        crc.update(forCrc.toByteArray());

        ByteBuffer out = ByteBuffer.allocate(16 + payload.length + 8);
        out.put(head.array());
        out.put(payload);
        out.putInt((int) crc.getValue());
        out.put(SUFFIX);
        return out.array();
    }

    private byte[] aes(int mode, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(mode, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("AES-" + (mode == Cipher.ENCRYPT_MODE ? "Verschlüsselung" : "Entschlüsselung")
                    + " fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private static byte[] stripVersionHeader(byte[] payload) {
        if (payload.length > 15 && payload[0] == '3' && payload[1] == '.' && payload[2] == '3') {
            byte[] stripped = new byte[payload.length - 15];
            System.arraycopy(payload, 15, stripped, 0, stripped.length);
            return stripped;
        }
        return payload;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static boolean allZero(byte[] data) {
        for (byte b : data) {
            if (b != 0) {
                return false;
            }
        }
        return true;
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
