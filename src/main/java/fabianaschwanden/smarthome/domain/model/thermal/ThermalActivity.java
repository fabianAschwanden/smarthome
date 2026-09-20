package fabianaschwanden.smarthome.domain.model.thermal;

/**
 * Was ein temperaturführendes Gerät gerade tut – heizen, kühlen oder nichts. Gemeinsam
 * für Klimaanlage und Wellness-Anlagen, damit die Oberfläche beide gleich einfärben kann:
 * Man soll auf einen Blick sehen, ob gerade Energie in Wärme oder Kälte fliesst.
 *
 * <p>{@code IDLE} heisst nicht «aus»: Ein Whirlpool mit Soll 20 °C bei 31 °C Wasser ist an
 * und tut trotzdem nichts – das Wasser kühlt von selbst.
 */
public enum ThermalActivity {
    HEATING,
    COOLING,
    IDLE
}
