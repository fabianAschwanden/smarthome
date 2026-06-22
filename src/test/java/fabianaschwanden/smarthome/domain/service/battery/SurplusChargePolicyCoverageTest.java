package fabianaschwanden.smarthome.domain.service.battery;

import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Deckt {@link SurplusChargePolicy#decide} als {@link QuarkusTest} ab, damit die
 * Entscheidungslogik ins quarkus-jacoco zählt (der reine JUnit-Test daneben tut das nicht).
 */
@QuarkusTest
class SurplusChargePolicyCoverageTest {

    private final SurplusChargePolicy policy = new SurplusChargePolicy(1500, 300);

    @Test
    void ueberObererSchwelleSchaltetEin() {
        assertEquals(RelayState.ON, policy.decide(1501, RelayState.OFF));
        // Grenzwert (>=) schaltet ebenfalls ein.
        assertEquals(RelayState.ON, policy.decide(1500, RelayState.OFF));
    }

    @Test
    void unterUntererSchwelleSchaltetAus() {
        assertEquals(RelayState.OFF, policy.decide(299, RelayState.ON));
        // Grenzwert (<=) schaltet ebenfalls aus.
        assertEquals(RelayState.OFF, policy.decide(300, RelayState.ON));
    }

    @Test
    void imHystereseBandWirdDerZustandGehalten() {
        assertEquals(RelayState.ON, policy.decide(900, RelayState.ON));
        assertEquals(RelayState.OFF, policy.decide(900, RelayState.OFF));
    }

    @Test
    void verdrehteSchwellenWerdenAbgelehnt() {
        assertThrows(IllegalArgumentException.class, () -> new SurplusChargePolicy(300, 1500));
    }
}
