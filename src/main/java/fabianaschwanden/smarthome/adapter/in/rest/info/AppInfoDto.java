package fabianaschwanden.smarthome.adapter.in.rest.info;

/**
 * Release-Info der laufenden Instanz: Version (Git-Tag ohne „v", z. B. „1.5.3"; „dev"
 * bei lokalen Builds) und Build-Zeitpunkt (ISO-8601, leer wenn unbekannt).
 */
public record AppInfoDto(String version, String builtAt) {
}
