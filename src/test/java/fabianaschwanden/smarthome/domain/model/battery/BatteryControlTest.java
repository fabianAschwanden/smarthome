package fabianaschwanden.smarthome.domain.model.battery;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class BatteryControlTest {

    private final Instant t0 = Instant.parse("2026-06-22T10:00:00Z");
    private final Instant t1 = Instant.parse("2026-06-22T11:00:00Z");

    @Test
    void gueltigeInstanzBautKorrekt() {
        BatteryControl b = new BatteryControl(ControlMode.AUTO, RelayState.ON, t0);
        assertEquals(ControlMode.AUTO, b.mode());
        assertEquals(RelayState.ON, b.desiredState());
        assertEquals(t0, b.changedAt());
    }

    @Test
    void modeDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class, () -> new BatteryControl(null, RelayState.ON, t0));
    }

    @Test
    void desiredStateDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class, () -> new BatteryControl(ControlMode.AUTO, null, t0));
    }

    @Test
    void changedAtDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class, () -> new BatteryControl(ControlMode.AUTO, RelayState.ON, null));
    }

    @Test
    void initialIstManuellUndAus() {
        BatteryControl b = BatteryControl.initial(t0);
        assertEquals(ControlMode.MANUAL, b.mode());
        assertEquals(RelayState.OFF, b.desiredState());
        assertEquals(t0, b.changedAt());
    }

    @Test
    void withModeLiefertNeueInstanz() {
        BatteryControl b = BatteryControl.initial(t0);
        BatteryControl b2 = b.withMode(ControlMode.AUTO, t1);
        assertEquals(ControlMode.AUTO, b2.mode());
        assertEquals(RelayState.OFF, b2.desiredState());
        assertEquals(t1, b2.changedAt());
        assertEquals(ControlMode.MANUAL, b.mode()); // Original unverändert
    }

    @Test
    void withStateLiefertNeueInstanz() {
        BatteryControl b = BatteryControl.initial(t0);
        BatteryControl b2 = b.withState(RelayState.ON, t1);
        assertEquals(RelayState.ON, b2.desiredState());
        assertEquals(ControlMode.MANUAL, b2.mode());
        assertEquals(t1, b2.changedAt());
        assertEquals(RelayState.OFF, b.desiredState()); // Original unverändert
    }
}
