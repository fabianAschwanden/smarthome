package fabianaschwanden.smarthome.domain.port.in.forecast;

import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;

/**
 * Treiber-Port (Use Case): die aktuelle Ladeempfehlung als Batterie-Zeitplan übernehmen.
 *
 * <p>Dieser Use Case schaltet nichts selbst – er legt einen Zeitplan über den bestehenden
 * Use Case 14 an, dem die Ausführung (Manuell-Modus setzen, danach zurück) vollständig
 * gehört. Kein zweiter Schaltpfad (SPEC §3.4).
 */
public interface ApplyRecommendation {

    /**
     * @return der angelegte Zeitplan
     * @throws NoRecommendationAvailable wenn gerade keine Empfehlung vorliegt (REST: 409)
     */
    BatterySchedule apply();
}
