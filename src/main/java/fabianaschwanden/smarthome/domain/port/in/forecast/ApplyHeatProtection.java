package fabianaschwanden.smarthome.domain.port.in.forecast;

import fabianaschwanden.smarthome.domain.model.coverschedule.CoverSchedule;

import java.util.List;

/**
 * Treiber-Port (Use Case): das Beschattungsfenster als Storen-Zeitsteuerung übernehmen.
 *
 * <p>Wirft {@link NoHeatProtectionAvailable}, wenn gerade kein Fenster vorliegt.
 */
public interface ApplyHeatProtection {

    /** Legt je Store einen Countdown fürs Zufahren und einen fürs Öffnen an. */
    List<CoverSchedule> applyHeatProtection();
}
