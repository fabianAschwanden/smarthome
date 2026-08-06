package fabianaschwanden.smarthome.adapter.in.homekit;

import io.github.hapjava.server.HomekitAuthInfo;
import io.github.hapjava.server.impl.HomekitServer;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kopplungszustand im Arbeitsspeicher – bewusst nur für den Spike (Etappe 1).
 *
 * <p>Nach einem Neustart sind MAC, Schlüssel und Pairings weg, die Home-App meldet dann
 * „keine Antwort" und das Gerät muss neu gekoppelt werden. Etappe 2 ersetzt das durch die
 * Persistenz über {@code HomekitStateRepository} (SPEC §4) – ohne die wäre die Bridge
 * nach jedem Deploy verloren.
 */
class InMemoryAuthInfo implements HomekitAuthInfo {

    private final String pin;
    private final String mac = HomekitServer.generateMac();
    private final BigInteger salt = HomekitServer.generateSalt();
    private final byte[] privateKey;
    private final Map<String, byte[]> users = new LinkedHashMap<>();

    InMemoryAuthInfo(String pin) throws InvalidAlgorithmParameterException {
        this.pin = pin;
        this.privateKey = HomekitServer.generateKey();
    }

    @Override public String getPin() { return pin; }
    @Override public String getMac() { return mac; }
    @Override public BigInteger getSalt() { return salt; }
    @Override public byte[] getPrivateKey() { return privateKey; }

    @Override public void createUser(String username, byte[] publicKey) { users.put(username, publicKey); }
    @Override public void removeUser(String username) { users.remove(username); }
    @Override public byte[] getUserPublicKey(String username) { return users.get(username); }
    @Override public Collection<String> listUsers() { return users.keySet(); }
    @Override public boolean hasUser() { return !users.isEmpty(); }
}
