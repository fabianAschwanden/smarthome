package fabianaschwanden.smarthome.adapter.out.irradiance;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Die Eckdaten der eigenen PV-Anlage unter {@code forecast.plant.*}. Der Standort kommt
 * aus der bestehenden {@code weather.*}-Konfiguration – er ist derselbe.
 *
 * <p>Die Defaults sind neutrale Platzhalter (Süd, 30°, 10 kWp): Neigung und Ausrichtung
 * sind standortbezogen und gehören in die gitignorte {@code config/}. Bleiben sie falsch,
 * lernt das Anlagenprofil den Fehler mit ein und kaschiert ihn im Faktor – die Prognose
 * wirkt dann plausibel, obwohl die Eingangsdaten nicht stimmen (SPEC §8).
 */
@ConfigMapping(prefix = "forecast.plant")
public interface IrradianceConfig {

    /** Modulneigung in Grad; 0 = flach, 90 = senkrecht. */
    @WithDefault("30")
    double tilt();

    /** Ausrichtung nach Open-Meteo-Konvention: 0 = Süd, −90 = Ost, +90 = West. */
    @WithDefault("0")
    double azimuth();

    /** Nennleistung in kWp – nur für den Cold-Start-Fallback (SPEC §3.1). */
    @WithDefault("10")
    double kwp();
}
