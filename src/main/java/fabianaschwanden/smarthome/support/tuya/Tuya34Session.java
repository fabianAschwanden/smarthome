package fabianaschwanden.smarthome.support.tuya;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Eine 3.4-Sitzung gegen ein Tuya-Gerät: öffnet die TCP-Verbindung, führt den
 * Session-Key-Handshake (03/04/05) durch und sendet danach Datenbefehle mit dem
 * abgeleiteten Session-Key. {@link AutoCloseable} – pro Operation eine kurze Sitzung.
 *
 * <p>Session-Key = AES-ECB(localNonce XOR remoteNonce, key=local-key).
 */
public final class Tuya34Session implements AutoCloseable {

    private static final int PORT = 6668;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] localKey;
    private final Socket socket;
    private int sequence = 1;
    private byte[] sessionKey;

    private Tuya34Session(byte[] localKey, Socket socket) {
        this.localKey = localKey;
        this.socket = socket;
    }

    /** Öffnet die Verbindung und verhandelt den Session-Key. */
    public static Tuya34Session open(String address, String localKey, int timeoutMs) throws Exception {
        byte[] key = localKey.getBytes(StandardCharsets.UTF_8);
        if (key.length != 16) {
            throw new IllegalArgumentException("local-key muss 16 Byte lang sein");
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(address, PORT), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        Tuya34Session session = new Tuya34Session(key, socket);
        session.negotiate();
        return session;
    }

    private void negotiate() throws Exception {
        byte[] localNonce = new byte[16];
        RANDOM.nextBytes(localNonce);

        // Schritt 1: START mit der localNonce als ROH-Bytes (nicht verschlüsselt!).
        byte[] start = Tuya34Protocol.frame(localKey, Tuya34Protocol.SESS_KEY_NEG_START,
                sequence++, localNonce);
        byte[] respFrame = exchange(start);

        // Schritt 2: RESP enthält (verschlüsselt) remoteNonce + HMAC(localNonce).
        byte[] respPayload = Tuya34Protocol.decrypt(localKey, Tuya34Protocol.payloadOf(respFrame));
        byte[] remoteNonce = new byte[16];
        System.arraycopy(respPayload, 0, remoteNonce, 0, 16);

        // Schritt 3: FINISH mit HMAC(remoteNonce).
        byte[] finishPayload = Tuya34Protocol.encrypt(localKey, Tuya34Protocol.hmac(localKey, remoteNonce));
        byte[] finish = Tuya34Protocol.frame(localKey, Tuya34Protocol.SESS_KEY_NEG_FINISH,
                sequence++, finishPayload);
        socket.getOutputStream().write(finish);
        socket.getOutputStream().flush();

        // Session-Key = AES-ECB(localNonce XOR remoteNonce, key=local-key), ein Block, kein Padding.
        byte[] xor = new byte[16];
        for (int i = 0; i < 16; i++) {
            xor[i] = (byte) (localNonce[i] ^ remoteNonce[i]);
        }
        this.sessionKey = Tuya34Protocol.encryptBlock(localKey, xor);
    }

    /** Sendet einen Datenbefehl (mit Session-Key) und gibt den entschlüsselten JSON-Payload. */
    public String send(int command, String json) throws Exception {
        byte[] payload = Tuya34Protocol.encrypt(sessionKey, Tuya34Protocol.utf8(json));
        byte[] frame = Tuya34Protocol.frame(sessionKey, command, sequence++, payload);
        byte[] response = exchange(frame);
        byte[] respPayload = Tuya34Protocol.payloadOf(response);
        if (respPayload.length == 0) {
            return null;
        }
        byte[] plain = Tuya34Protocol.decrypt(sessionKey, respPayload);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private byte[] exchange(byte[] frame) throws Exception {
        OutputStream out = socket.getOutputStream();
        out.write(frame);
        out.flush();
        InputStream in = socket.getInputStream();
        byte[] buffer = new byte[2048];
        int read = in.read(buffer);
        if (read <= 0) {
            return new byte[0];
        }
        byte[] response = new byte[read];
        System.arraycopy(buffer, 0, response, 0, read);
        return response;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (Exception ignored) {
            // best effort
        }
    }
}
