package fabianaschwanden.smarthome.application.service.appliance;

import fabianaschwanden.smarthome.domain.model.appliance.Appliance;
import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.model.appliance.Temperature;
import fabianaschwanden.smarthome.domain.port.in.appliance.ApplianceNotFound;
import fabianaschwanden.smarthome.domain.port.in.appliance.FunctionNotSupported;
import fabianaschwanden.smarthome.domain.port.in.appliance.TemperatureNotSupported;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDevice;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class ApplianceControlServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-19T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void schaltetFunktionUndLiestZurueck() {
        FakeAppliance wp = new FakeAppliance("whirlpool",
                EnumSet.of(ApplianceFunction.PUMP, ApplianceFunction.HEATER), true);
        ApplianceControlService service = new ApplianceControlService(List.of(wp), clock);

        Appliance result = service.switchFunction("whirlpool", ApplianceFunction.HEATER, FunctionState.ON);

        assertEquals(FunctionState.ON, result.functions().get(ApplianceFunction.HEATER));
        assertEquals(FunctionState.OFF, result.functions().get(ApplianceFunction.PUMP));
    }

    @Test
    void nichtVorhandeneFunktionWirdAbgelehnt() {
        FakeAppliance pool = new FakeAppliance("pool", EnumSet.of(ApplianceFunction.PUMP), false);
        ApplianceControlService service = new ApplianceControlService(List.of(pool), clock);

        assertThrows(FunctionNotSupported.class,
                () -> service.switchFunction("pool", ApplianceFunction.MASSAGE, FunctionState.ON));
    }

    @Test
    void unbekannteAnlageWirftNotFound() {
        ApplianceControlService service = new ApplianceControlService(
                List.of(new FakeAppliance("a", EnumSet.of(ApplianceFunction.PUMP), false)), clock);
        assertThrows(ApplianceNotFound.class,
                () -> service.switchFunction("x", ApplianceFunction.PUMP, FunctionState.ON));
    }

    @Test
    void offlineMeldetLetztenZustand() {
        FakeAppliance wp = new FakeAppliance("whirlpool", EnumSet.of(ApplianceFunction.LIGHT), false);
        ApplianceControlService service = new ApplianceControlService(List.of(wp), clock);
        service.switchFunction("whirlpool", ApplianceFunction.LIGHT, FunctionState.ON);

        wp.reachable = false;
        Appliance status = service.list().get(0);

        assertFalse(status.online());
        assertEquals(FunctionState.ON, status.functions().get(ApplianceFunction.LIGHT));
    }

    @Test
    void beheizteAnlageSetztSollTemperatur() {
        FakeAppliance wp = new FakeAppliance("whirlpool", EnumSet.of(ApplianceFunction.HEATER), true);
        ApplianceControlService service = new ApplianceControlService(List.of(wp), clock);

        Appliance result = service.setTargetTemperature("whirlpool", 36);

        assertEquals(36, result.temperature().target());
        assertEquals(36, wp.target);
    }

    @Test
    void sollTemperaturAusserhalbDesBereichsWirdAbgelehnt() {
        FakeAppliance wp = new FakeAppliance("whirlpool", EnumSet.of(ApplianceFunction.HEATER), true);
        ApplianceControlService service = new ApplianceControlService(List.of(wp), clock);

        // Erst lesen, damit der bekannte Temperaturbereich (30..40) gefüllt ist.
        service.list();

        assertThrows(IllegalArgumentException.class, () -> service.setTargetTemperature("whirlpool", 99));
    }

    @Test
    void temperaturAufNichtBeheizterAnlageWirdAbgelehnt() {
        FakeAppliance pool = new FakeAppliance("pool", EnumSet.of(ApplianceFunction.PUMP), false);
        ApplianceControlService service = new ApplianceControlService(List.of(pool), clock);

        assertThrows(TemperatureNotSupported.class, () -> service.setTargetTemperature("pool", 25));
        assertNull(service.list().get(0).temperature());
    }

    private static final class FakeAppliance implements ApplianceDevice {
        private final String id;
        private final Map<ApplianceFunction, FunctionState> states = new EnumMap<>(ApplianceFunction.class);
        private final boolean heated;
        private int target = 35;
        private boolean reachable = true;

        FakeAppliance(String id, Set<ApplianceFunction> functions, boolean heated) {
            this.id = id;
            this.heated = heated;
            functions.forEach(f -> states.put(f, FunctionState.OFF));
        }

        @Override public String id() { return id; }
        @Override public String name() { return id; }
        @Override public String room() { return ""; }
        @Override public Set<ApplianceFunction> functions() { return states.keySet(); }
        @Override public boolean heated() { return heated; }
        @Override public void apply(ApplianceFunction function, FunctionState state) {
            states.put(function, state);
        }
        @Override public void applyTargetTemp(int target) { this.target = target; }
        @Override public Optional<State> readState() {
            if (!reachable) {
                return Optional.empty();
            }
            Temperature temp = heated ? new Temperature(target, target, 30, 40) : null;
            return Optional.of(new State(new EnumMap<>(states), temp));
        }
    }
}
