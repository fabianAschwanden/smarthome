package fabianaschwanden.smarthome.adapter.out.energy.mock;

import java.time.LocalTime;

/**
 * Erzeugt plausible, zeitabhängige Mock-Werte (tagesähnlicher PV-Bogen,
 * schwankender Hausverbrauch). Nur für die Mock-Adapter.
 */
final class SyntheticGrid {

    private SyntheticGrid() {
    }

    /** Glockenförmiger PV-Verlauf: 0 W nachts, ~6 kW Peak zur Mittagszeit. */
    static double pvWatt() {
        double hour = LocalTime.now().toSecondOfDay() / 3600.0;
        double bell = Math.exp(-Math.pow(hour - 13.0, 2) / 6.0); // Peak um 13 Uhr
        double peak = 6000.0;
        double jitter = (Math.random() - 0.5) * 200.0;
        return Math.max(0.0, peak * bell + jitter);
    }

    /** Grundlast ~400 W plus zufällige Lastspitzen. */
    static double consumptionWatt() {
        double base = 400.0;
        double spike = Math.random() < 0.2 ? 1500.0 * Math.random() : 0.0;
        double jitter = (Math.random() - 0.5) * 100.0;
        return Math.max(0.0, base + spike + jitter);
    }
}
