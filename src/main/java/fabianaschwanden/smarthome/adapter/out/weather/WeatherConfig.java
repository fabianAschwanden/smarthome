package fabianaschwanden.smarthome.adapter.out.weather;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Konfiguration des Wetter-Standorts unter {@code weather.*}. Der konkrete Standort
 * (Name + Koordinaten) ist ortsbezogen und gehört in die gitignored config/ bzw. Env –
 * hier nur neutrale CH-Default-Koordinaten als Platzhalter.
 */
@ConfigMapping(prefix = "weather")
public interface WeatherConfig {

    @WithDefault("Mein Zuhause")
    String location();

    @WithDefault("46.95")
    double latitude();

    @WithDefault("7.45")
    double longitude();
}
