package fabianaschwanden.smarthome.adapter.out.nativeview;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;

/**
 * Konfiguration der nativen Geräte-Weboberflächen unter {@code nativeview.targets[i].*}.
 * Die Ziel-URL ({@code url}) zeigt auf das Gerät im LAN (z. B. {@code http://192.168.1.124})
 * und wird NUR serverseitig vom Reverse-Proxy genutzt – sie verlässt das Backend nie
 * Richtung Frontend.
 *
 * <p>Prefix bewusst {@code nativeview} statt {@code native}: SmallRye validiert bei
 * {@code @ConfigMapping} alle Keys mit dem Prefix strikt, und {@code native.encoding}
 * (JVM-/Surefire-System-Property) würde sonst fälschlich als unbekannter Key abgelehnt.
 */
@ConfigMapping(prefix = "nativeview")
public interface NativeViewConfig {

    List<Target> targets();

    interface Target {
        String id();

        String name();

        /** Material-/Emoji-Icon-Hinweis fürs Frontend (optional). */
        @WithDefault("")
        String icon();

        /** Basis-URL des Geräts im LAN (Reverse-Proxy-Ziel), z. B. {@code http://192.168.1.124}. */
        String url();

        /** Startpfad/-seite, die im iframe geladen wird (z. B. {@code /index.shtml}). */
        @WithDefault("/")
        String path();
    }
}
