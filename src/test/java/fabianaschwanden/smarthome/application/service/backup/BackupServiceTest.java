package fabianaschwanden.smarthome.application.service.backup;

import fabianaschwanden.smarthome.domain.model.alert.AlertSettings;
import fabianaschwanden.smarthome.domain.model.backup.BackupSnapshot;
import fabianaschwanden.smarthome.domain.model.backup.RestoreSummary;
import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatteryScheduleType;
import fabianaschwanden.smarthome.domain.model.coverschedule.CoverSchedule;
import fabianaschwanden.smarthome.domain.model.coverschedule.CoverScheduleType;
import fabianaschwanden.smarthome.domain.model.itemimage.ItemImage;
import fabianaschwanden.smarthome.domain.model.schedule.ScheduleType;
import fabianaschwanden.smarthome.domain.model.schedule.SwitchSchedule;
import fabianaschwanden.smarthome.domain.model.tuya.SwitchState;
import fabianaschwanden.smarthome.domain.port.out.alert.AlertSettingsRepository;
import fabianaschwanden.smarthome.domain.port.out.batteryschedule.BatteryScheduleRepository;
import fabianaschwanden.smarthome.domain.port.out.coverschedule.CoverScheduleRepository;
import fabianaschwanden.smarthome.domain.port.out.itemimage.ItemImageRepository;
import fabianaschwanden.smarthome.domain.port.out.schedule.ScheduleRepository;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class BackupServiceTest {

    private static SwitchSchedule schedule(String switchId) {
        return new SwitchSchedule(UUID.randomUUID(), switchId, ScheduleType.SCHEDULE,
                SwitchState.ON, true, LocalTime.of(6, 30), Set.of(), null, null, null, null);
    }

    private BackupService service(FakeAlert alert, FakeSwitchRepo switches, FakeBatteryRepo battery,
            FakeCoverRepo cover, FakeImageRepo images) {
        return new BackupService(alert, switches, battery, cover, images);
    }

    @Test
    void exportSammeltAlleKategorien() {
        FakeAlert alert = new FakeAlert();
        alert.save(new AlertSettings(true, "topic"));
        FakeSwitchRepo switches = new FakeSwitchRepo();
        switches.save(schedule("lampe"));
        FakeImageRepo images = new FakeImageRepo();
        images.save(new ItemImage("lampe", "data:image/jpeg;base64,AAA", Instant.parse("2026-07-19T10:00:00Z")));

        BackupSnapshot snapshot = service(alert, switches, new FakeBatteryRepo(), new FakeCoverRepo(), images)
                .exportData();

        assertTrue(snapshot.alertSettings().isPresent());
        assertEquals(1, snapshot.switchSchedules().size());
        assertEquals(1, snapshot.itemImages().size());
        assertEquals(0, snapshot.batterySchedules().size());
    }

    @Test
    void restoreErsetztDenBestand() {
        FakeSwitchRepo switches = new FakeSwitchRepo();
        SwitchSchedule alt = schedule("alte-lampe");
        switches.save(alt);
        FakeImageRepo images = new FakeImageRepo();
        images.save(new ItemImage("alt", "data:image/jpeg;base64,BBB", Instant.parse("2026-01-01T00:00:00Z")));

        SwitchSchedule neu = schedule("neue-lampe");
        BatterySchedule akku = new BatterySchedule(UUID.randomUUID(), BatteryScheduleType.SCHEDULE,
                RelayState.ON, true, LocalTime.of(11, 0), Set.of(), null);
        CoverSchedule store = new CoverSchedule(UUID.randomUUID(), "store-1", CoverScheduleType.SCHEDULE,
                80, true, LocalTime.of(7, 0), Set.of(), null);
        BackupSnapshot snapshot = new BackupSnapshot(
                Optional.of(new AlertSettings(false, "neu")),
                List.of(neu), List.of(akku), List.of(store), List.of());

        RestoreSummary summary = service(new FakeAlert(), switches, new FakeBatteryRepo(),
                new FakeCoverRepo(), images).restore(snapshot);

        assertEquals(1, summary.switchSchedules());
        assertEquals(1, summary.batterySchedules());
        assertEquals(1, summary.coverSchedules());
        assertEquals(0, summary.itemImages());
        assertTrue(summary.alertSettings());
        // Alte Einträge sind weg – das Backup ist die Wahrheit.
        assertEquals(List.of(neu), switches.all());
        assertEquals(0, images.all().size());
    }

    // ---- In-Memory-Fakes ----

    private static final class FakeAlert implements AlertSettingsRepository {
        private AlertSettings value;
        @Override public Optional<AlertSettings> load() { return Optional.ofNullable(value); }
        @Override public void save(AlertSettings settings) { this.value = settings; }
    }

    private static final class FakeSwitchRepo implements ScheduleRepository {
        private final Map<UUID, SwitchSchedule> store = new HashMap<>();
        @Override public SwitchSchedule save(SwitchSchedule s) { store.put(s.id(), s); return s; }
        @Override public Optional<SwitchSchedule> byId(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<SwitchSchedule> forSwitch(String switchId) { return List.of(); }
        @Override public List<SwitchSchedule> all() { return List.copyOf(store.values()); }
        @Override public List<SwitchSchedule> allEnabled() { return List.copyOf(store.values()); }
        @Override public void delete(UUID id) { store.remove(id); }
    }

    private static final class FakeBatteryRepo implements BatteryScheduleRepository {
        private final Map<UUID, BatterySchedule> store = new HashMap<>();
        @Override public BatterySchedule save(BatterySchedule s) { store.put(s.id(), s); return s; }
        @Override public Optional<BatterySchedule> byId(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<BatterySchedule> all() { return List.copyOf(store.values()); }
        @Override public List<BatterySchedule> allEnabled() { return List.copyOf(store.values()); }
        @Override public void delete(UUID id) { store.remove(id); }
    }

    private static final class FakeCoverRepo implements CoverScheduleRepository {
        private final Map<UUID, CoverSchedule> store = new HashMap<>();
        @Override public CoverSchedule save(CoverSchedule s) { store.put(s.id(), s); return s; }
        @Override public Optional<CoverSchedule> byId(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<CoverSchedule> all() { return List.copyOf(store.values()); }
        @Override public List<CoverSchedule> allEnabled() { return List.copyOf(store.values()); }
        @Override public void delete(UUID id) { store.remove(id); }
    }

    private static final class FakeImageRepo implements ItemImageRepository {
        private final Map<String, ItemImage> store = new HashMap<>();
        @Override public ItemImage save(ItemImage image) { store.put(image.itemId(), image); return image; }
        @Override public Optional<ItemImage> byItemId(String itemId) { return Optional.ofNullable(store.get(itemId)); }
        @Override public List<ItemImage> all() { return List.copyOf(store.values()); }
        @Override public void delete(String itemId) { store.remove(itemId); }
    }
}
