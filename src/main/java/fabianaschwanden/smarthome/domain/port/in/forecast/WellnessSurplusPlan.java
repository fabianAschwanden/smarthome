package fabianaschwanden.smarthome.domain.port.in.forecast;

import fabianaschwanden.smarthome.domain.model.applianceschedule.ApplianceSchedule;

import java.util.List;

/**
 * Treiber-Port (Use Case): die Wellness-Heizung in ein Überschussfenster legen.
 *
 * <p>Der Dienst schaltet nichts selbst; er legt Schaltaufträge an, die die
 * Wellness-Zeitsteuerung ausführt.
 */
public interface WellnessSurplusPlan {

    /**
     * Legt je konfigurierter Anlage einen Auftrag zum Ein- und einen zum Ausschalten der
     * Heizung an.
     *
     * @throws NoRecommendationAvailable wenn gerade kein Überschussfenster vorliegt.
     */
    List<ApplianceSchedule> applyWellnessSurplus();
}
