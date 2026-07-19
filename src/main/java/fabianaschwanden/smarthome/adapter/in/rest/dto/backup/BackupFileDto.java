package fabianaschwanden.smarthome.adapter.in.rest.dto.backup;

import fabianaschwanden.smarthome.domain.model.alert.AlertSettings;
import fabianaschwanden.smarthome.domain.model.backup.BackupSnapshot;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatteryScheduleType;
import fabianaschwanden.smarthome.domain.model.coverschedule.CoverSchedule;
import fabianaschwanden.smarthome.domain.model.coverschedule.CoverScheduleType;
import fabianaschwanden.smarthome.domain.model.itemimage.ItemImage;
import fabianaschwanden.smarthome.domain.model.schedule.ScheduleType;
import fabianaschwanden.smarthome.domain.model.schedule.SwitchSchedule;
import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Backup-Datei (Transport): versioniertes JSON aller Nutzerdaten. Zeiten als
 * ISO-Strings, Wochentage als Namen ("MONDAY"). {@code schemaVersion} sichert die
 * Import-Kompatibilität ab; {@code energy_sample} ist bewusst nicht enthalten.
 */
public record BackupFileDto(
        int schemaVersion,
        String exportedAt,
        AlertSettingsBackup alertSettings,
        List<SwitchScheduleBackup> switchSchedules,
        List<BatteryScheduleBackup> batterySchedules,
        List<CoverScheduleBackup> coverSchedules,
        List<ItemImageBackup> itemImages) {

    public static final int SCHEMA_VERSION = 1;

    public static BackupFileDto from(BackupSnapshot s, Instant exportedAt) {
        return new BackupFileDto(
                SCHEMA_VERSION,
                exportedAt.toString(),
                s.alertSettings().map(AlertSettingsBackup::from).orElse(null),
                s.switchSchedules().stream().map(SwitchScheduleBackup::from).toList(),
                s.batterySchedules().stream().map(BatteryScheduleBackup::from).toList(),
                s.coverSchedules().stream().map(CoverScheduleBackup::from).toList(),
                s.itemImages().stream().map(ItemImageBackup::from).toList());
    }

    /** Baut den Domänen-Schnappschuss; wirft bei fremder {@code schemaVersion}. */
    public BackupSnapshot toSnapshot() {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Nicht unterstützte Backup-Version " + schemaVersion + " (erwartet " + SCHEMA_VERSION + ")");
        }
        return new BackupSnapshot(
                Optional.ofNullable(alertSettings).map(AlertSettingsBackup::toDomain),
                list(switchSchedules).stream().map(SwitchScheduleBackup::toDomain).toList(),
                list(batterySchedules).stream().map(BatteryScheduleBackup::toDomain).toList(),
                list(coverSchedules).stream().map(CoverScheduleBackup::toDomain).toList(),
                list(itemImages).stream().map(ItemImageBackup::toDomain).toList());
    }

    private static <T> List<T> list(List<T> value) {
        return value == null ? List.of() : value;
    }

    public record AlertSettingsBackup(boolean enabled, String ntfyTopic) {
        static AlertSettingsBackup from(AlertSettings a) {
            return new AlertSettingsBackup(a.enabled(), a.ntfyTopic());
        }

        AlertSettings toDomain() {
            return new AlertSettings(enabled, ntfyTopic);
        }
    }

    public record SwitchScheduleBackup(
            String id,
            String switchId,
            String type,
            String action,
            boolean enabled,
            String time,
            List<String> weekdays,
            String fireAt,
            String windowStart,
            String windowEnd,
            Integer pulseSeconds) {

        static SwitchScheduleBackup from(SwitchSchedule s) {
            return new SwitchScheduleBackup(s.id().toString(), s.switchId(), s.type().name(),
                    s.action().name(), s.enabled(), isoTime(s.time()), days(s.weekdays()),
                    isoInstant(s.fireAt()), isoTime(s.windowStart()), isoTime(s.windowEnd()),
                    s.pulseSeconds());
        }

        SwitchSchedule toDomain() {
            return new SwitchSchedule(UUID.fromString(id), switchId, ScheduleType.valueOf(type),
                    SwitchState.valueOf(action), enabled, parseTime(time), parseDays(weekdays),
                    parseInstant(fireAt), parseTime(windowStart), parseTime(windowEnd), pulseSeconds);
        }
    }

    public record BatteryScheduleBackup(
            String id, String type, String action, boolean enabled, String time,
            List<String> weekdays, String fireAt) {

        static BatteryScheduleBackup from(BatterySchedule s) {
            return new BatteryScheduleBackup(s.id().toString(), s.type().name(), s.action().name(),
                    s.enabled(), isoTime(s.time()), days(s.weekdays()), isoInstant(s.fireAt()));
        }

        BatterySchedule toDomain() {
            return new BatterySchedule(UUID.fromString(id), BatteryScheduleType.valueOf(type),
                    RelayState.valueOf(action), enabled, parseTime(time), parseDays(weekdays),
                    parseInstant(fireAt));
        }
    }

    public record CoverScheduleBackup(
            String id, String coverId, String type, int position, boolean enabled, String time,
            List<String> weekdays, String fireAt) {

        static CoverScheduleBackup from(CoverSchedule s) {
            return new CoverScheduleBackup(s.id().toString(), s.coverId(), s.type().name(),
                    s.position(), s.enabled(), isoTime(s.time()), days(s.weekdays()),
                    isoInstant(s.fireAt()));
        }

        CoverSchedule toDomain() {
            return new CoverSchedule(UUID.fromString(id), coverId, CoverScheduleType.valueOf(type),
                    position, enabled, parseTime(time), parseDays(weekdays), parseInstant(fireAt));
        }
    }

    public record ItemImageBackup(String itemId, String dataUrl, String updatedAt) {
        static ItemImageBackup from(ItemImage i) {
            return new ItemImageBackup(i.itemId(), i.dataUrl(), i.updatedAt().toString());
        }

        ItemImage toDomain() {
            return new ItemImage(itemId, dataUrl, Instant.parse(updatedAt));
        }
    }

    // ---- gemeinsame (De-)Serialisierung ----

    private static String isoTime(LocalTime value) {
        return value == null ? null : value.toString();
    }

    private static LocalTime parseTime(String value) {
        return value == null ? null : LocalTime.parse(value);
    }

    private static String isoInstant(Instant value) {
        return value == null ? null : value.toString();
    }

    private static Instant parseInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    private static List<String> days(Set<DayOfWeek> value) {
        return value == null ? List.of() : value.stream().map(Enum::name).sorted().toList();
    }

    private static Set<DayOfWeek> parseDays(List<String> value) {
        return value == null
                ? Set.of()
                : value.stream().map(DayOfWeek::valueOf).collect(Collectors.toUnmodifiableSet());
    }
}
