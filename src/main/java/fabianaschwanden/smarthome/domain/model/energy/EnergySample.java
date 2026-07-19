package fabianaschwanden.smarthome.domain.model.energy;

import java.time.Instant;

/**
 * Ein periodisch aufgezeichneter Messpunkt der Energiequelle: Momentanleistung von
 * PV-Produktion und Hausverbrauch zu einem Zeitpunkt. Grundlage für die Zeitreihe
 * (Tages-/Wochen-/Monatsverlauf); die Aggregation zu Energie (kWh) passiert im
 * {@code EnergyHistoryService}.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record EnergySample(Instant timestamp, double pvWatt, double consumptionWatt) {

    public EnergySample {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp darf nicht null sein");
        }
    }
}
