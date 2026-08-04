package fabianaschwanden.smarthome.adapter.out.irradiance.mock;

import fabianaschwanden.smarthome.domain.model.forecast.IrradiancePoint;
import fabianaschwanden.smarthome.domain.model.forecast.IrradianceSeries;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MockIrradianceGatewayTest {

    private final MockIrradianceGateway gateway = new MockIrradianceGateway();

    @Test
    void liefertVergangenheitUndVorhersage() {
        IrradianceSeries series = gateway.fetch().orElseThrow();

        assertFalse(series.past().isEmpty(), "Lernen braucht Vergangenheit");
        assertFalse(series.forecast().isEmpty(), "Prognose braucht Zukunft");
        assertTrue(series.past().get(series.past().size() - 1).hour()
                .isBefore(series.forecast().get(0).hour()));
    }

    @Test
    void nachtsIstEsDunkel() {
        assertEquals(0.0, MockIrradianceGateway.gtiAt(0), 0.0001);
        assertEquals(0.0, MockIrradianceGateway.gtiAt(3), 0.0001);
        assertEquals(0.0, MockIrradianceGateway.gtiAt(23), 0.0001);
    }

    @Test
    void mittagsIstDerScheitel() {
        double peak = MockIrradianceGateway.gtiAt((int) MockIrradianceGateway.PEAK_HOUR);

        assertEquals(MockIrradianceGateway.PEAK_WATT_PER_SQM, peak, 0.0001);
        assertTrue(peak > MockIrradianceGateway.gtiAt(9));
        assertTrue(peak > MockIrradianceGateway.gtiAt(17));
    }

    @Test
    void kurveSteigtUndFaelltMonoton() {
        for (int hour = 1; hour <= MockIrradianceGateway.PEAK_HOUR; hour++) {
            assertTrue(MockIrradianceGateway.gtiAt(hour) >= MockIrradianceGateway.gtiAt(hour - 1),
                    "steigend bis zum Scheitel, Stunde " + hour);
        }
        for (int hour = (int) MockIrradianceGateway.PEAK_HOUR + 1; hour <= 23; hour++) {
            assertTrue(MockIrradianceGateway.gtiAt(hour) <= MockIrradianceGateway.gtiAt(hour - 1),
                    "fallend nach dem Scheitel, Stunde " + hour);
        }
    }

    @Test
    void zweiAbrufeLiefernDieselbeKurve() {
        // Deterministisch: sonst waeren Tests, die darauf aufbauen, nicht stabil.
        IrradianceSeries first = gateway.fetch().orElseThrow();
        IrradianceSeries second = gateway.fetch().orElseThrow();

        assertEquals(first.forecast().size(), second.forecast().size());
        for (int i = 0; i < first.forecast().size(); i++) {
            IrradiancePoint a = first.forecast().get(i);
            IrradiancePoint b = second.forecast().get(i);
            assertEquals(a.hour(), b.hour());
            assertEquals(a.gtiWattPerSqm(), b.gtiWattPerSqm(), 0.0001);
        }
    }

    @Test
    void stundenSindLueckenlosUndAufsystemZoneBezogen() {
        IrradianceSeries series = gateway.fetch().orElseThrow();
        ZoneId zone = ZoneId.systemDefault();

        for (int i = 1; i < series.forecast().size(); i++) {
            assertEquals(3600, series.forecast().get(i).hour().getEpochSecond()
                    - series.forecast().get(i - 1).hour().getEpochSecond(), "Stunde " + i);
        }
        assertEquals(0, series.forecast().get(0).hour().atZone(zone).getMinute());
    }
}
