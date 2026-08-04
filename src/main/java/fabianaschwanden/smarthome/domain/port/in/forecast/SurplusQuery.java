package fabianaschwanden.smarthome.domain.port.in.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.ChargeRecommendation;
import fabianaschwanden.smarthome.domain.model.forecast.ConsumptionBaseline;
import fabianaschwanden.smarthome.domain.model.forecast.SurplusWindow;

import java.util.List;
import java.util.Optional;

/** Treiber-Port (Use Case): erwartete Überschussfenster und die daraus abgeleitete Empfehlung. */
public interface SurplusQuery {

    /** Alle Fenster über der Schwelle, chronologisch. */
    List<SurplusWindow> windows();

    /** Das energiereichste Fenster als Ladeempfehlung; {@code empty}, wenn es keines gibt. */
    Optional<ChargeRecommendation> recommendation();

    /** Der typische Verbrauch, gegen den gerechnet wird – fürs UI als Vergleichskurve. */
    Optional<ConsumptionBaseline> baseline();
}
