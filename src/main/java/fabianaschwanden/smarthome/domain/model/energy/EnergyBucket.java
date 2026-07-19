package fabianaschwanden.smarthome.domain.model.energy;

import java.time.Instant;

/**
 * Ein Zeitabschnitt der Energie-Historie mit der zugehörigen Energie in kWh: je nach
 * Bereich eine Stunde (Tag) oder ein Tag (Woche/Monat). {@code start} ist der Beginn
 * des Abschnitts; {@code selfUseKwh} ist die Eigennutzung (direkt verbrauchte
 * PV-Produktion, Integral von min(pv, verbrauch)). Leere Abschnitte haben 0 kWh.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record EnergyBucket(Instant start, double pvKwh, double consumptionKwh, double selfUseKwh) {

    public EnergyBucket {
        if (start == null) {
            throw new IllegalArgumentException("start darf nicht null sein");
        }
    }
}
