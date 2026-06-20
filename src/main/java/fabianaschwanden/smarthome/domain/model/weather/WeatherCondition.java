package fabianaschwanden.smarthome.domain.model.weather;

/**
 * Wetterzustand, normalisiert aus dem WMO-Wettercode (Open-Meteo). Framework-frei;
 * die Zuordnung WMO-Code -> Zustand liegt im Adapter, hier nur die fachlichen Werte.
 */
public enum WeatherCondition {
    CLEAR,
    MAINLY_CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    SHOWERS,
    THUNDERSTORM,
    UNKNOWN
}
