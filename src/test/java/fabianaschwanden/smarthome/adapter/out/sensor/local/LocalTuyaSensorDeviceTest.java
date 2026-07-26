package fabianaschwanden.smarthome.adapter.out.sensor.local;

import fabianaschwanden.smarthome.domain.port.out.sensor.SensorDevice;
import fabianaschwanden.smarthome.support.tuya.TuyaDiscovery;
import fabianaschwanden.smarthome.support.tuya.TuyaSidecarClient;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft das DP-Mapping und das (nicht-blockierende) Cache-Verhalten des Tuya-Sensor-Adapters
 * gegen einen Fake-Sidecar – ohne echtes Gerät. Bewusst über den 3.4-Pfad (Sidecar), damit
 * kein realer Socket nötig ist; die Cache-Logik ist versionsunabhängig.
 *
 * <p>{@code @QuarkusTest}, damit die Coverage-Messung den echten Adapter-Code erfasst.</p>
 */
@QuarkusTest
class LocalTuyaSensorDeviceTest {

    // temp dp1=232 (÷10 = 23.2 °C), humidity dp2=61 %
    private static final String DPS_JSON = "{\"dps\": {\"1\": 232, \"2\": 61, \"9\": \"c\"}}";

    /** Fake-Client: liefert festes DP-JSON, statt das Netz zu nutzen. */
    private static TuyaSidecarClient fakeSidecar(String dpsJson) {
        return new TuyaSidecarClient("http://unused") {
            @Override
            public Optional<String> readDps(String deviceId, String localKey, String ip, String version) {
                return dpsJson == null ? Optional.empty() : Optional.of(dpsJson);
            }
        };
    }

    private static LocalTuyaSensorDevice sensor(TuyaSidecarClient sidecar, java.util.function.LongSupplier clock) {
        return new LocalTuyaSensorDevice(
                "aussen", "Aussen", "Garten", "dev-id", "0123456789abcdef", "192.168.113.248",
                "3.4", 1, 2, 10, new TuyaDiscovery(false), sidecar, clock);
    }

    /** Pollt bis zu 5 s auf den asynchron gefüllten Cache. */
    private static SensorDevice.Reading awaitReading(LocalTuyaSensorDevice dev) {
        for (int i = 0; i < 50; i++) {
            Optional<SensorDevice.Reading> r = dev.read();
            if (r.isPresent()) {
                return r.get();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new AssertionError("Cache wurde nicht gefüllt");
    }

    @Test
    void read_cacht_und_mappt_temperatur_und_feuchte() {
        LocalTuyaSensorDevice dev = sensor(fakeSidecar(DPS_JSON), System::currentTimeMillis);

        // Erststart: leer, stösst Hintergrund-Refresh an.
        assertTrue(dev.read().isEmpty());

        // Cache füllt sich asynchron (Virtual-Thread) – kurz pollen.
        SensorDevice.Reading reading = awaitReading(dev);
        assertEquals(23.2, reading.temperature(), 0.001);  // 232 ÷ 10
        assertEquals(61, reading.humidity());
    }

    @Test
    void meldet_offline_wenn_letzter_erfolgreicher_read_zu_alt() {
        long[] clock = {0};
        LocalTuyaSensorDevice dev = sensor(fakeSidecar(DPS_JSON), () -> clock[0]);

        // Bei t=0 füllt sich der Cache (Read erfolgreich).
        assertTrue(awaitReading(dev).temperature() > 0, "frisch gecacht -> online");

        // 30 min + 1 ms ohne (relevanten) erfolgreichen Read -> offline.
        clock[0] = 1_800_001;
        assertTrue(dev.read().isEmpty(), "zu alter Cache -> offline");
    }

    @Test
    void bleibt_offline_wenn_geraet_nie_antwortet() {
        LocalTuyaSensorDevice dev = sensor(fakeSidecar(null), System::currentTimeMillis);

        // Sidecar liefert nie etwas -> Cache bleibt leer, kein Wert.
        for (int i = 0; i < 5; i++) {
            assertTrue(dev.read().isEmpty());
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
