package fabianaschwanden.smarthome.adapter.out.appliance.pending;

import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceUnavailable;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Platzhalter-Anlage: meldet ihre Stammdaten/Funktionen, gilt aber als offline
 * ({@code readState()} leer) und lehnt jeden Steuerbefehl mit {@link ApplianceUnavailable} ab.
 *
 * <p>{@code @QuarkusTest}, damit die Coverage ins Quarkus-JaCoCo zählt.
 */
@QuarkusTest
class PendingApplianceDeviceTest {

    private PendingApplianceDevice device() {
        return new PendingApplianceDevice(
                "app-1", "Sauna", "Keller", Set.of(ApplianceFunction.HEATER, ApplianceFunction.LIGHT), true);
    }

    @Test
    void liefertStammdatenUndFunktionen() {
        PendingApplianceDevice device = device();
        assertEquals("app-1", device.id());
        assertEquals("Sauna", device.name());
        assertEquals("Keller", device.room());
        assertTrue(device.heated());
        assertEquals(Set.of(ApplianceFunction.HEATER, ApplianceFunction.LIGHT), device.functions());
    }

    @Test
    void readStateIstLeerWeilOffline() {
        assertTrue(device().readState().isEmpty());
    }

    @Test
    void applyWirftUnavailable() {
        PendingApplianceDevice device = device();
        ApplianceUnavailable ex =
                assertThrows(ApplianceUnavailable.class, () -> device.apply(ApplianceFunction.HEATER, FunctionState.ON));
        assertTrue(ex.getMessage().contains("Sauna"));
    }

    @Test
    void applyTargetTempWirftUnavailable() {
        PendingApplianceDevice device = device();
        assertThrows(ApplianceUnavailable.class, () -> device.applyTargetTemp(40));
    }
}
