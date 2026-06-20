package fabianaschwanden.smarthome.application.service.energy;

import fabianaschwanden.smarthome.domain.model.energy.EnergySnapshot;
import fabianaschwanden.smarthome.domain.model.energy.PowerReading;
import fabianaschwanden.smarthome.domain.model.energy.PowerSource;
import fabianaschwanden.smarthome.domain.port.out.energy.EnergySourceGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentEnergyServiceTest {

    private final Instant now = Instant.parse("2026-06-19T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @Test
    void liestAlleQuellenUndVergleicht() {
        EnergySourceGateway fronius = stub(PowerReading.of(PowerSource.FRONIUS, now, -4000, 5000, null, 1000));
        EnergySourceGateway smartfox = stub(PowerReading.of(PowerSource.SMARTFOX, now, -3900, 4900, null, 1000));

        CurrentEnergyService service = new CurrentEnergyService(List.of(fronius, smartfox), clock);
        EnergySnapshot snapshot = service.currentEnergy();

        assertEquals(now, snapshot.timestamp());
        assertEquals(2, snapshot.readings().size());
        assertTrue(snapshot.comparison().isPresent());
    }

    private static EnergySourceGateway stub(PowerReading reading) {
        return new EnergySourceGateway() {
            @Override
            public PowerSource source() {
                return reading.source();
            }

            @Override
            public PowerReading read() {
                return reading;
            }
        };
    }
}
