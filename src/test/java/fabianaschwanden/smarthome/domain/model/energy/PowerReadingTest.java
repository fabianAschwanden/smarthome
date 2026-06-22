package fabianaschwanden.smarthome.domain.model.energy;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PowerReadingTest {

    private final Instant now = Instant.parse("2026-06-22T10:00:00Z");

    @Test
    void gueltigeInstanzBautKorrekt() {
        PowerReading r = new PowerReading(PowerSource.FRONIUS, now, 100, 2000, 500.0, 1600,
                ReadingStatus.OK, null);
        assertEquals(PowerSource.FRONIUS, r.source());
        assertEquals(now, r.timestamp());
        assertEquals(100, r.gridWatt());
        assertEquals(2000, r.pvWatt());
        assertEquals(500.0, r.batteryWatt());
        assertEquals(1600, r.consumptionWatt());
        assertEquals(ReadingStatus.OK, r.status());
    }

    @Test
    void sourceDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new PowerReading(null, now, 0, 0, null, 0, ReadingStatus.OK, null));
    }

    @Test
    void timestampDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new PowerReading(PowerSource.FRONIUS, null, 0, 0, null, 0, ReadingStatus.OK, null));
    }

    @Test
    void statusDarfNichtNullSein() {
        assertThrows(IllegalArgumentException.class,
                () -> new PowerReading(PowerSource.FRONIUS, now, 0, 0, null, 0, null, null));
    }

    @Test
    void ofSetztStatusOk() {
        PowerReading r = PowerReading.of(PowerSource.SMARTFOX, now, 50, 1000, 200.0, 750);
        assertEquals(ReadingStatus.OK, r.status());
        assertTrue(r.isOk());
        assertEquals(50, r.gridWatt());
        assertEquals(200.0, r.battery().orElseThrow());
        assertEquals(null, r.daily());
    }

    @Test
    void ofMitDailyEnergy() {
        PowerReading.DailyEnergy daily =
                new PowerReading.DailyEnergy(5000.0, 12000.0, 80.0, 60.0);
        PowerReading r = PowerReading.of(PowerSource.FRONIUS, now, 0, 1000, null, 1000, daily);
        assertEquals(daily, r.daily());
        assertEquals(5000.0, r.daily().productionWhToday());
        assertEquals(80.0, r.daily().autonomyPercent());
    }

    @Test
    void errorSetztStatusError() {
        PowerReading r = PowerReading.error(PowerSource.SMARTFOX, now);
        assertEquals(ReadingStatus.ERROR, r.status());
        assertFalse(r.isOk());
        assertEquals(0, r.gridWatt());
        assertEquals(null, r.daily());
    }

    @Test
    void batteryLeerWennNull() {
        PowerReading r = PowerReading.of(PowerSource.FRONIUS, now, 0, 0, null, 0);
        assertTrue(r.battery().isEmpty());
    }

    @Test
    void batteryVorhandenWennGesetzt() {
        PowerReading r = PowerReading.of(PowerSource.FRONIUS, now, 0, 0, -300.0, 0);
        assertTrue(r.battery().isPresent());
        assertEquals(-300.0, r.battery().get());
    }
}
