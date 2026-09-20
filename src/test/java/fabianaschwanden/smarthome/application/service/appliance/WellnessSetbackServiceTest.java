package fabianaschwanden.smarthome.application.service.appliance;

import fabianaschwanden.smarthome.application.config.WellnessConfig;
import fabianaschwanden.smarthome.domain.model.appliance.Appliance;
import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.port.in.appliance.ControlAppliances;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Abendabsenkung. Ein Gecko-Spa haelt seinen Sollwert rund um die Uhr - ohne sie
 * heizt es die ganze Nacht auf den Tageswert weiter.
 */
class WellnessSetbackServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    /** Uhr auf der angegebenen Ortszeit am 10. August. */
    private static Clock uhrUm(String ortszeit) {
        return Clock.fixed(
                java.time.LocalDate.of(2026, 8, 10).atTime(LocalTime.parse(ortszeit)).atZone(ZONE).toInstant(),
                ZONE);
    }

    private FakeAppliances appliances;

    private WellnessSetbackService service(String ortszeit) {
        appliances = new FakeAppliances();
        return new WellnessSetbackService(appliances, new FakeConfig(), uhrUm(ortszeit));
    }

    @Test
    void senkt_ab_der_konfigurierten_uhrzeit_ab() {
        service("16:00").tick();

        assertEquals(List.of("whirlpool:20"), appliances.calls);
    }

    @Test
    void senkt_davor_noch_nicht_ab() {
        service("15:59").tick();

        assertTrue(appliances.calls.isEmpty());
    }

    @Test
    void senkt_auch_spaeter_am_abend_noch_ab() {
        // Startet die App erst um 20 Uhr neu, muss die Absenkung nachgeholt werden -
        // sonst heizte die Anlage bis zum naechsten Morgen weiter.
        service("20:30").tick();

        assertEquals(List.of("whirlpool:20"), appliances.calls);
    }

    @Test
    void senkt_hoechstens_einmal_je_tag_ab() {
        // Sonst ueberschriebe der Minutentakt jede Aenderung von Hand.
        WellnessSetbackService service = service("18:00");

        service.tick();
        service.tick();
        service.tick();

        assertEquals(1, appliances.calls.size());
    }

    @Test
    void laesst_sich_von_einer_stoerung_nicht_aufhalten() {
        WellnessSetbackService service = service("18:00");
        appliances.fail = true;

        service.tick();

        assertEquals(1, appliances.calls.size());
    }

    @Test
    void ueberspringt_eine_stillgelegte_anlage() {
        WellnessSetbackService service = service("18:00");
        appliances.deactivated.add("whirlpool");

        service.tick();

        assertTrue(appliances.calls.isEmpty());
    }

    /** Whirlpool: tags 25, Ueberschuss 33, ab 16:00 dann 20 °C. */
    private static final class FakeConfig implements WellnessConfig {

        @Override
        public LocalTime setbackTime() {
            return LocalTime.of(16, 0);
        }

        @Override
        public String setbackCheckInterval() {
            return "1m";
        }

        @Override
        public List<Entry> appliances() {
            return List.of(new Entry() {
                @Override
                public String id() {
                    return "whirlpool";
                }

                @Override
                public int baseTemp() {
                    return 25;
                }

                @Override
                public int surplusTemp() {
                    return 33;
                }

                @Override
                public int nightTemp() {
                    return 20;
                }
            });
        }
    }

    private static final class FakeAppliances implements ControlAppliances {
        private final List<String> calls = new ArrayList<>();
        private final java.util.Set<String> deactivated = new java.util.HashSet<>();
        private boolean fail;

        @Override
        public List<Appliance> list() {
            return List.of();
        }

        @Override
        public Appliance switchFunction(String id, ApplianceFunction function, FunctionState state) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Appliance setTargetTemperature(String id, int target) {
            calls.add(id + ":" + target);
            if (fail) {
                throw new IllegalStateException("Anlage nicht erreichbar");
            }
            return new Appliance(id, id, "", true, true, Instant.EPOCH, new java.util.EnumMap<>(ApplianceFunction.class), null);
        }

        @Override
        public OptionalInt pendingTarget(String id) {
            return OptionalInt.empty();
        }
        @Override
        public Appliance setActive(String id, boolean active) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isActive(String id) {
            return !deactivated.contains(id);
        }
    }
}
