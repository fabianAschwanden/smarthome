package fabianaschwanden.smarthome.application.config;

import io.smallrye.config.ConfigMapping;

import java.util.List;

/**
 * Welche Wellness-Anlagen im Überschussfenster aufgeheizt werden – und auf welche
 * Temperaturen.
 *
 * <p>Zwei feste Werte je Anlage statt einer relativen Anhebung: So ist vorhersagbar, was
 * passiert. Eine Anhebung «um N Grad» klänge freundlicher, liefe aber aus dem Ruder,
 * sobald jemand während des Fensters selbst verstellt – zurückgerechnet würde dann auf
 * einen Wert, den es nie gab.
 *
 * <p>Eigener Schlüssel-Stamm und nicht unterhalb von {@code appliance}: Diesen Teilbaum
 * beansprucht das {@code @ConfigMapping} der Gerätekonfiguration vollständig.
 *
 * <p>Liegt bewusst neben den Diensten und nicht in {@code application.service}: Dort
 * verlangt die Architekturregel {@code @ApplicationScoped}, und eine Konfiguration ist
 * kein Dienst.
 */
@ConfigMapping(prefix = "wellness-surplus")
public interface WellnessSurplusConfig {

    List<Entry> appliances();

    interface Entry {

        /** Muss einer {@code appliance.devices[i].id} entsprechen. */
        String id();

        /** Soll-Temperatur ausserhalb des Fensters (°C). */
        int baseTemp();

        /** Soll-Temperatur im Überschussfenster (°C). */
        int surplusTemp();
    }
}
