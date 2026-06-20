package fabianaschwanden.smarthome.adapter.out.appliance.local;

import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDevice;
import fabianaschwanden.smarthome.support.tuya.TuyaSidecarClient;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft das JSON-Parsing (Funktions-Mapping, Temperatur) und das Cache-Verhalten des
 * Gecko-Adapters gegen einen Fake-Sidecar – ohne echtes Spa.
 *
 * <p>Bewusst {@code @QuarkusTest}, damit die Coverage-Messung (Quarkus-JaCoCo) den
 * echten Adapter-Code erfasst.</p>
 */
@QuarkusTest
class LocalGeckoApplianceDeviceTest {

    private static final String SPA_JSON = """
            {"current": 32.5, "target": 18.0, "operation": "Heating",
             "pumps": {"P1": true, "P2": false}, "lights": {"LI": true},
             "watercare": "Standard", "online": true}
            """;

    /** Fake-Client: liefert festes JSON, statt das Netz zu nutzen. */
    private static TuyaSidecarClient fakeSidecar(String readJson) {
        return new TuyaSidecarClient("http://unused") {
            @Override
            public Optional<String> readSpa(String ip, String ident, String name) {
                return Optional.of(readJson);
            }

            @Override
            public Optional<String> controlSpa(String ip, String ident, String name,
                    Integer target, String pumpKey, String lightKey, Boolean on) {
                return Optional.of(readJson);
            }

            @Override
            public Optional<String> controlSpaWaterCare(String ip, String ident, String name, String mode) {
                return Optional.of(readJson);
            }
        };
    }

    /** Pollt bis zu 5 s auf den asynchron gefüllten Cache. */
    private static ApplianceDevice.State awaitState(LocalGeckoApplianceDevice dev) {
        for (int i = 0; i < 50; i++) {
            Optional<ApplianceDevice.State> s = dev.readState();
            if (s.isPresent()) {
                return s.get();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new AssertionError("Cache wurde nicht gefüllt");
    }

    private static LocalGeckoApplianceDevice whirlpool(TuyaSidecarClient sidecar) {
        return new LocalGeckoApplianceDevice(
                "whirlpool", "Whirlpool", "Wellness",
                Set.of(ApplianceFunction.PUMP, ApplianceFunction.HEATER,
                        ApplianceFunction.LIGHT, ApplianceFunction.MASSAGE, ApplianceFunction.FILTER),
                true, 30, 40, "1.2.3.4", "SPA-IDENT", "P1", "P2", "LI", sidecar);
    }

    @Test
    void readState_cacht_und_mappt_funktionen_und_temperatur() {
        LocalGeckoApplianceDevice dev = whirlpool(fakeSidecar(SPA_JSON));

        // Erststart: leer, stösst Hintergrund-Refresh an.
        assertTrue(dev.readState().isEmpty());

        // Cache füllt sich asynchron (Virtual-Thread) – kurz pollen.
        ApplianceDevice.State state = awaitState(dev);
        assertEquals(FunctionState.ON, state.functions().get(ApplianceFunction.PUMP));      // P1=true
        assertEquals(FunctionState.OFF, state.functions().get(ApplianceFunction.MASSAGE));  // P2=false
        assertEquals(FunctionState.ON, state.functions().get(ApplianceFunction.LIGHT));     // LI=true
        assertEquals(FunctionState.ON, state.functions().get(ApplianceFunction.FILTER));    // watercare=Standard
        assertEquals(18, state.temperature().target());   // Gerätewert unverfälscht
        assertEquals(33, state.temperature().current());  // 32.5 gerundet
        // Soll 18 < konfiguriertes min 30 -> min wird auf den gemeldeten Wert geweitet.
        assertEquals(18, state.temperature().min());
        assertEquals(40, state.temperature().max());
    }

    @Test
    void control_uebernimmt_antwort_als_frischen_cache() {
        LocalGeckoApplianceDevice dev = whirlpool(fakeSidecar(SPA_JSON));

        dev.apply(ApplianceFunction.LIGHT, FunctionState.ON);  // Antwort wird gecacht

        // Ohne Hintergrund-Refresh sofort verfügbar.
        ApplianceDevice.State state = dev.readState().orElseThrow();
        assertEquals(FunctionState.ON, state.functions().get(ApplianceFunction.LIGHT));
    }
}
