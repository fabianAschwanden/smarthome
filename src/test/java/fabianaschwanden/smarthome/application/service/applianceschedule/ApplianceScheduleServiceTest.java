package fabianaschwanden.smarthome.application.service.applianceschedule;

import fabianaschwanden.smarthome.domain.model.appliance.Appliance;
import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.model.applianceschedule.ApplianceSchedule;
import fabianaschwanden.smarthome.domain.port.in.appliance.ControlAppliances;
import fabianaschwanden.smarthome.domain.port.out.applianceschedule.ApplianceScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Der Ticker der Wellness-Zeitsteuerung. */
class ApplianceScheduleServiceTest {

    private static final Instant JETZT = Instant.parse("2026-08-07T12:00:00Z");
    private static final Clock UHR = Clock.fixed(JETZT, ZoneOffset.UTC);

    private FakeRepository repository;
    private FakeAppliances appliances;
    private ApplianceScheduleService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        appliances = new FakeAppliances();
        service = new ApplianceScheduleService(repository, appliances, UHR);
    }

    @Test
    void schaltet_einen_faelligen_auftrag() {
        service.save(ApplianceSchedule.countdown(
                "whirlpool", ApplianceFunction.HEATER, FunctionState.ON, JETZT.minusSeconds(60)));

        service.tick();

        assertEquals(List.of("whirlpool:HEATER:ON"), appliances.calls);
    }

    @Test
    void laesst_einen_zukuenftigen_auftrag_liegen() {
        service.save(ApplianceSchedule.countdown(
                "whirlpool", ApplianceFunction.HEATER, FunctionState.ON, JETZT.plusSeconds(3600)));

        service.tick();

        assertTrue(appliances.calls.isEmpty());
        assertTrue(repository.all().get(0).enabled());
    }

    @Test
    void schaltet_einen_auftrag_nur_einmal() {
        service.save(ApplianceSchedule.countdown(
                "whirlpool", ApplianceFunction.HEATER, FunctionState.ON, JETZT.minusSeconds(60)));

        service.tick();
        service.tick();

        assertEquals(1, appliances.calls.size());
        assertFalse(repository.all().get(0).enabled());
    }

    @Test
    void gibt_einen_fehlgeschlagenen_auftrag_auf() {
        // Sonst versuchte es der Ticker alle paar Sekunden erneut, und ein defektes
        // Geraet fuellte das Log.
        appliances.fail = true;
        service.save(ApplianceSchedule.countdown(
                "whirlpool", ApplianceFunction.HEATER, FunctionState.ON, JETZT.minusSeconds(60)));

        service.tick();
        service.tick();

        assertEquals(1, appliances.calls.size());
        assertFalse(repository.all().get(0).enabled());
    }

    private static final class FakeRepository implements ApplianceScheduleRepository {
        private final Map<UUID, ApplianceSchedule> entries = new HashMap<>();

        @Override
        public ApplianceSchedule save(ApplianceSchedule schedule) {
            entries.put(schedule.id(), schedule);
            return schedule;
        }

        @Override
        public List<ApplianceSchedule> all() {
            return new ArrayList<>(entries.values());
        }

        @Override
        public List<ApplianceSchedule> allEnabled() {
            return all().stream().filter(ApplianceSchedule::enabled).toList();
        }

        @Override
        public void delete(UUID id) {
            entries.remove(id);
        }
    }

    private static final class FakeAppliances implements ControlAppliances {
        private final List<String> calls = new ArrayList<>();
        private boolean fail;

        @Override
        public List<Appliance> list() {
            return List.of();
        }

        @Override
        public Appliance switchFunction(String id, ApplianceFunction function, FunctionState state) {
            calls.add(id + ":" + function + ":" + state);
            if (fail) {
                throw new IllegalStateException("Anlage nicht erreichbar");
            }
            return null;
        }

        @Override
        public Appliance setTargetTemperature(String id, int target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.OptionalInt pendingTarget(String id) {
            // Kein offener Temperaturwunsch - dieser Test dreht sich um Schaltauftraege.
            return java.util.OptionalInt.empty();
        }
    }
}
