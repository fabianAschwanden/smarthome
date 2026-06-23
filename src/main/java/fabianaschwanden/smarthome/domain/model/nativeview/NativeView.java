package fabianaschwanden.smarthome.domain.model.nativeview;

/**
 * Eine native Geräte-Weboberfläche, die im Dashboard als Kachel eingebettet wird
 * (z. B. die SMARTFOX-Web-UI). Das Frontend kennt nur ID/Name/Icon; die Ziel-URL des
 * Geräts bleibt serverseitig (Reverse-Proxy unter {@code /native/<id>/}), damit die
 * Haus-IP nicht ins Frontend/Repo gelangt und der Zugriff auch remote über Port 8080
 * läuft.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record NativeView(String id, String name, String icon) {

    public NativeView {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id darf nicht leer sein");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name darf nicht leer sein");
        }
        if (icon == null) {
            icon = "";
        }
    }
}
