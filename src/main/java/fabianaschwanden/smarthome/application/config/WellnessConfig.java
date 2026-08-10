package fabianaschwanden.smarthome.application.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.LocalTime;
import java.util.List;

/**
 * Die Temperaturen der Wellness-Anlagen: was normal gilt, worauf im Überschuss geheizt
 * wird und worauf am Abend abgesenkt wird.
 *
 * <p>Feste Werte statt relativer Anhebungen, damit vorhersagbar ist, was passiert. Eine
 * Rückrechnung «minus N Grad» liefe aus dem Ruder, sobald jemand zwischendurch selbst
 * verstellt.
 *
 * <p>Eigener Schlüssel-Stamm und nicht unterhalb von {@code appliance}: Diesen Teilbaum
 * beansprucht das {@code @ConfigMapping} der Gerätekonfiguration vollständig. Liegt
 * bewusst neben den Diensten und nicht in {@code application.service} – dort verlangt die
 * Architekturregel {@code @ApplicationScoped}, und eine Konfiguration ist kein Dienst.
 */
@ConfigMapping(prefix = "wellness")
public interface WellnessConfig {

    /**
     * Ab wann abgesenkt wird (Ortszeit). Ein Gecko-Spa hält seinen Sollwert rund um die
     * Uhr – ohne Absenkung heizt es die ganze Nacht auf den Tageswert.
     */
    @WithDefault("16:00")
    LocalTime setbackTime();

    /** Wie oft geprüft wird, ob die Absenkzeit erreicht ist. */
    @WithDefault("1m")
    String setbackCheckInterval();

    List<Entry> appliances();

    interface Entry {

        /** Muss einer {@code appliance.devices[i].id} entsprechen. */
        String id();

        /** Soll-Temperatur tagsüber ausserhalb des Überschussfensters (°C). */
        int baseTemp();

        /** Soll-Temperatur im Überschussfenster (°C). */
        int surplusTemp();

        /** Soll-Temperatur ab der Absenkzeit bis zum nächsten Morgen (°C). */
        int nightTemp();
    }
}
