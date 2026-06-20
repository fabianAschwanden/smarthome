package fabianaschwanden.smarthome.domain.model.energy;

import java.time.Instant;
import java.util.Optional;

/**
 * Momentaufnahme der Leistungswerte einer Quelle, auf eine einheitliche
 * Vorzeichenkonvention normalisiert (siehe SPEC §2):
 * <ul>
 *   <li>{@code gridWatt}: + = Netzbezug, − = Einspeisung</li>
 *   <li>{@code pvWatt}: + = PV-Produktion</li>
 *   <li>{@code batteryWatt}: + = Laden, − = Entladen (optional)</li>
 *   <li>{@code consumptionWatt}: + = Hausverbrauch</li>
 * </ul>
 * Value Object: immutable {@code record}, Invarianten im Compact-Constructor.
 */
public record PowerReading(
        PowerSource source,
        Instant timestamp,
        double gridWatt,
        double pvWatt,
        Double batteryWatt,
        double consumptionWatt,
        ReadingStatus status,
        DailyEnergy daily) {

    public PowerReading {
        if (source == null) {
            throw new IllegalArgumentException("source darf nicht null sein");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp darf nicht null sein");
        }
        if (status == null) {
            throw new IllegalArgumentException("status darf nicht null sein");
        }
    }

    /**
     * Tages- und Relativ-Kennzahlen, sofern die Quelle sie liefert (Fronius PowerFlow).
     * Alle Felder optional ({@code null}): {@code productionWhToday}/{@code totalWh} sind
     * Energiemengen (Wh), {@code autonomyPercent} = Selbstversorgung,
     * {@code selfConsumptionPercent} = Eigennutzung.
     */
    public record DailyEnergy(
            Double productionWhToday,
            Double totalWh,
            Double autonomyPercent,
            Double selfConsumptionPercent) {
    }

    /** Erfolgreiche Messung (ohne Tageswerte). */
    public static PowerReading of(
            PowerSource source,
            Instant timestamp,
            double gridWatt,
            double pvWatt,
            Double batteryWatt,
            double consumptionWatt) {
        return of(source, timestamp, gridWatt, pvWatt, batteryWatt, consumptionWatt, null);
    }

    /** Erfolgreiche Messung mit Tages-/Relativ-Kennzahlen. */
    public static PowerReading of(
            PowerSource source,
            Instant timestamp,
            double gridWatt,
            double pvWatt,
            Double batteryWatt,
            double consumptionWatt,
            DailyEnergy daily) {
        return new PowerReading(
                source, timestamp, gridWatt, pvWatt, batteryWatt, consumptionWatt, ReadingStatus.OK, daily);
    }

    /** Fehlmessung (Quelle nicht erreichbar / nicht interpretierbar). */
    public static PowerReading error(PowerSource source, Instant timestamp) {
        return new PowerReading(source, timestamp, 0, 0, null, 0, ReadingStatus.ERROR, null);
    }

    public Optional<Double> battery() {
        return Optional.ofNullable(batteryWatt);
    }

    public boolean isOk() {
        return status == ReadingStatus.OK;
    }
}
