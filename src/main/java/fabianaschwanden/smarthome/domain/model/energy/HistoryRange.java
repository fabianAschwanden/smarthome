package fabianaschwanden.smarthome.domain.model.energy;

import java.time.temporal.ChronoUnit;

/**
 * Zeitbereich der Energie-Historie mit der jeweiligen Bucket-Granularität:
 * <ul>
 *   <li>{@code DAY} – der laufende Tag, in Stunden-Buckets (bis 24).</li>
 *   <li>{@code WEEK} – die letzten 7 Tage, in Tages-Buckets.</li>
 *   <li>{@code MONTH} – die letzten 30 Tage, in Tages-Buckets.</li>
 * </ul>
 * Bucket-Grenzen werden zeitzonenbewusst im {@code EnergyHistoryService} gebildet.
 */
public enum HistoryRange {

    DAY(ChronoUnit.HOURS, 24),
    WEEK(ChronoUnit.DAYS, 7),
    MONTH(ChronoUnit.DAYS, 30);

    private final ChronoUnit bucket;
    private final int bucketCount;

    HistoryRange(ChronoUnit bucket, int bucketCount) {
        this.bucket = bucket;
        this.bucketCount = bucketCount;
    }

    /** Länge eines Buckets (Stunde bei {@code DAY}, Tag sonst). */
    public ChronoUnit bucket() {
        return bucket;
    }

    /** Anzahl Buckets über den gesamten Bereich. */
    public int bucketCount() {
        return bucketCount;
    }

    /** Parst den REST-Query-Parameter (case-insensitive); wirft bei Unbekanntem. */
    public static HistoryRange fromParam(String value) {
        if (value == null || value.isBlank()) {
            return DAY;
        }
        return switch (value.trim().toUpperCase()) {
            case "DAY" -> DAY;
            case "WEEK" -> WEEK;
            case "MONTH" -> MONTH;
            default -> throw new IllegalArgumentException("Unbekannter Bereich: " + value);
        };
    }
}
