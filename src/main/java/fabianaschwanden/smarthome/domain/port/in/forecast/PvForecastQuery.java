package fabianaschwanden.smarthome.domain.port.in.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.PvForecast;

import java.util.Optional;

/**
 * Treiber-Port (Use Case): die aktuelle PV-Ertragsprognose für heute und morgen.
 *
 * <p>{@code empty} heisst „noch nie gerechnet" – etwa direkt nach dem Start, bevor der
 * erste Abruf der Strahlungsdaten durch ist. Eine veraltete Prognose wird dagegen
 * ausgeliefert und trägt ihr Alter selbst mit ({@code computedAt}).
 */
public interface PvForecastQuery {

    Optional<PvForecast> currentForecast();
}
