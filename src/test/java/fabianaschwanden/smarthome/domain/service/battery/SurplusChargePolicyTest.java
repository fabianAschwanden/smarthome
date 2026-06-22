package fabianaschwanden.smarthome.domain.service.battery;

import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class SurplusChargePolicyTest {

    private final SurplusChargePolicy policy = new SurplusChargePolicy(1500, 300);

    @Test
    void schaltetEinAbObererSchwelle() {
        assertEquals(RelayState.ON, policy.decide(1500, RelayState.OFF));
        assertEquals(RelayState.ON, policy.decide(2000, RelayState.OFF));
    }

    @Test
    void schaltetAusAbUntererSchwelle() {
        assertEquals(RelayState.OFF, policy.decide(300, RelayState.ON));
        assertEquals(RelayState.OFF, policy.decide(-500, RelayState.ON));
    }

    @Test
    void haeltZustandImHystereseBand() {
        assertEquals(RelayState.ON, policy.decide(800, RelayState.ON));
        assertEquals(RelayState.OFF, policy.decide(800, RelayState.OFF));
    }

    @Test
    void lehntVerdrehteSchwellenAb() {
        assertThrows(IllegalArgumentException.class, () -> new SurplusChargePolicy(300, 1500));
    }
}
