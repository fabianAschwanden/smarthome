package fabianaschwanden.smarthome.adapter.out.climate.pending;

import fabianaschwanden.smarthome.domain.model.climate.ClimateMode;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateUnavailable;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Platzhalter-Klimaanlage: meldet ihre Stammdaten, gilt aber als offline
 * ({@code readState()} leer) und lehnt jeden Steuerbefehl mit {@link ClimateUnavailable} ab.
 *
 * <p>{@code @QuarkusTest}, damit die Coverage ins Quarkus-JaCoCo zählt.
 */
@QuarkusTest
class PendingClimateDeviceTest {

    private PendingClimateDevice device() {
        return new PendingClimateDevice("cli-1", "Wohnzimmer-Klima", "Wohnzimmer");
    }

    @Test
    void liefertStammdaten() {
        PendingClimateDevice device = device();
        assertEquals("cli-1", device.id());
        assertEquals("Wohnzimmer-Klima", device.name());
        assertEquals("Wohnzimmer", device.room());
    }

    @Test
    void readStateIstLeerWeilOffline() {
        assertTrue(device().readState().isEmpty());
    }

    @Test
    void applyPowerWirftUnavailable() {
        PendingClimateDevice device = device();
        ClimateUnavailable ex = assertThrows(ClimateUnavailable.class, () -> device.applyPower(true));
        assertTrue(ex.getMessage().contains("Wohnzimmer-Klima"));
    }

    @Test
    void applyModeWirftUnavailable() {
        PendingClimateDevice device = device();
        assertThrows(ClimateUnavailable.class, () -> device.applyMode(ClimateMode.COOL));
    }

    @Test
    void applyTargetTempWirftUnavailable() {
        PendingClimateDevice device = device();
        assertThrows(ClimateUnavailable.class, () -> device.applyTargetTemp(22));
    }
}
