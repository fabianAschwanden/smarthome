package fabianaschwanden.smarthome.application.service.appliance;

import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.model.appliance.Temperature;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDevice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Das Nachhalten der Soll-Temperatur: Die Anlage uebernimmt den Wert verzoegert, also
 * muss der Wunsch so lange wiederholt werden, bis sie ihn meldet.
 */
class ApplianceTargetReconcileTest {

    private static final Clock UHR = Clock.fixed(Instant.parse("2026-08-07T12:00:00Z"), ZoneOffset.UTC);

    private TraegeAnlage anlage;
    private ApplianceControlService service;

    @BeforeEach
    void setUp() {
        anlage = new TraegeAnlage();
        service = new ApplianceControlService(List.of(anlage), UHR, 3);
    }

    @Test
    void meldet_den_wunsch_als_offen_solange_die_anlage_ihn_nicht_uebernommen_hat() {
        // Genau hier lag der Fehler: Ohne diese Auskunft zeigte die Oberflaeche den
        // alten Wert, und der naechste Schritt rechnete wieder von dort.
        anlage.verzoegerung = 2;

        service.setTargetTemperature("whirlpool", 38);

        assertEquals(38, service.pendingTarget("whirlpool").getAsInt());
    }

    @Test
    void wiederholt_den_wunsch_bis_die_anlage_ihn_meldet() {
        anlage.verzoegerung = 2;

        service.setTargetTemperature("whirlpool", 38);
        service.reconcileTargets();
        assertTrue(service.pendingTarget("whirlpool").isPresent());

        service.reconcileTargets();

        assertFalse(service.pendingTarget("whirlpool").isPresent());
        assertEquals(38, anlage.gemeldeteSollTemp);
    }

    @Test
    void nimmt_einen_sprung_ueber_mehrere_grad_an() {
        // Der eigentliche Zweck: nicht mehr Grad fuer Grad, sondern der ganze Weg.
        anlage.verzoegerung = 1;

        service.setTargetTemperature("whirlpool", 40);
        service.reconcileTargets();

        assertEquals(40, anlage.gemeldeteSollTemp);
        assertFalse(service.pendingTarget("whirlpool").isPresent());
    }

    @Test
    void gibt_nach_den_erlaubten_versuchen_auf() {
        // Ewig zu wiederholen hiesse, der Oberflaeche einen Wert zu versprechen, den die
        // Anlage offensichtlich nicht annimmt.
        anlage.verzoegerung = 99;

        service.setTargetTemperature("whirlpool", 38);
        service.reconcileTargets();
        service.reconcileTargets();
        service.reconcileTargets();

        assertFalse(service.pendingTarget("whirlpool").isPresent());
    }

    @Test
    void haelt_den_wunsch_auch_fest_wenn_der_erste_befehl_scheitert() {
        anlage.scheitertEinmal = true;

        try {
            service.setTargetTemperature("whirlpool", 38);
        } catch (RuntimeException expected) {
            // Der Befehl scheitert - der Wunsch muss trotzdem bekannt sein.
        }

        assertEquals(38, service.pendingTarget("whirlpool").getAsInt());
    }

    /** Anlage, die eine Soll-Temperatur erst nach {@code verzoegerung} Befehlen meldet. */
    private static final class TraegeAnlage implements ApplianceDevice {
        private int gemeldeteSollTemp = 36;
        private int gewuenscht = 36;
        private int verzoegerung;
        private int befehle;
        private boolean scheitertEinmal;

        @Override
        public String id() {
            return "whirlpool";
        }

        @Override
        public String name() {
            return "Whirlpool";
        }

        @Override
        public String room() {
            return "Wellness";
        }

        @Override
        public Set<ApplianceFunction> functions() {
            return Set.of(ApplianceFunction.HEATER);
        }

        @Override
        public boolean heated() {
            return true;
        }

        @Override
        public void apply(ApplianceFunction function, FunctionState state) {
            // nicht Gegenstand dieses Tests
        }

        @Override
        public void applyTargetTemp(int target) {
            if (scheitertEinmal) {
                scheitertEinmal = false;
                throw new IllegalStateException("Anlage nicht erreichbar");
            }
            gewuenscht = target;
            befehle++;
            if (befehle >= verzoegerung) {
                gemeldeteSollTemp = gewuenscht;
            }
        }

        @Override
        public Optional<State> readState() {
            Map<ApplianceFunction, FunctionState> functions = new EnumMap<>(ApplianceFunction.class);
            functions.put(ApplianceFunction.HEATER, FunctionState.ON);
            return Optional.of(new State(functions, new Temperature(gemeldeteSollTemp, 30, 30, 40)));
        }
    }
}
