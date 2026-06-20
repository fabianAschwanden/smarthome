package fabianaschwanden.smarthome.architecture;

import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDeviceFactory;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateDeviceFactory;
import fabianaschwanden.smarthome.domain.port.out.cover.CoverDeviceFactory;
import fabianaschwanden.smarthome.domain.port.out.sensor.SensorDeviceFactory;
import fabianaschwanden.smarthome.domain.port.out.safety.SmokeDetectorDeviceFactory;
import fabianaschwanden.smarthome.domain.port.out.tuya.SwitchDeviceFactory;
import io.quarkus.arc.All;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SICHERHEITS-Test: Unit-/Integrationstests laufen im {@code %test}-Profil und dürfen
 * NIEMALS echte Geräte im LAN ansteuern (sonst würde der Build reale Schalter/Storen
 * ein- und ausschalten). Diese Tests stellen sicher, dass im Test ausschliesslich die
 * Mock-Adapter aktiv sind – unabhängig von Env-Var {@code SMARTHOME_REAL_DEVICES} oder
 * lokalen {@code config/}-Einträgen (durch {@code %test.smarthome.real-devices=false}).
 *
 * <p>Bricht der Build hier, ist die Test-Isolation verletzt – nicht ignorieren.
 */
@QuarkusTest
class NoRealDevicesInTestTest {

    @Inject
    @All
    List<SwitchDeviceFactory> switchFactories;

    @Inject
    @All
    List<CoverDeviceFactory> coverFactories;

    @Inject
    @All
    List<SensorDeviceFactory> sensorFactories;

    @Inject
    @All
    List<SmokeDetectorDeviceFactory> smokeFactories;

    @Inject
    @All
    List<ClimateDeviceFactory> climateFactories;

    @Inject
    @All
    List<ApplianceDeviceFactory> applianceFactories;

    @Test
    void nurMockAdapterImTest() {
        assertOnlyMock(switchFactories, "Switch");
        assertOnlyMock(coverFactories, "Cover");
        assertOnlyMock(sensorFactories, "Sensor");
        assertOnlyMock(smokeFactories, "SmokeDetector");
        assertOnlyMock(climateFactories, "Climate");
        assertOnlyMock(applianceFactories, "Appliance");
    }

    private static void assertOnlyMock(List<?> factories, String label) {
        assertFalse(factories.isEmpty(), label + ": keine Factory aktiv");
        for (Object factory : factories) {
            assertNotNull(factory, label + ": Factory-Liste enthält null");
            String name = factory.getClass().getName();
            assertTrue(name.contains(".mock."),
                    label + "-Factory im Test ist kein Mock: " + name
                            + " – Test-Isolation verletzt, echte Geräte könnten geschaltet werden!");
            assertFalse(name.contains(".local."),
                    label + "-Factory im Test ist ein echter LAN-Adapter: " + name);
        }
    }
}
