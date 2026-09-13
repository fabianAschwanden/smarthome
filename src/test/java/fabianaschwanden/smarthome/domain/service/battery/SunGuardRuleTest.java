package fabianaschwanden.smarthome.domain.service.battery;

import fabianaschwanden.smarthome.domain.model.battery.SunGuard;
import fabianaschwanden.smarthome.domain.model.battery.SunGuardAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Wann der Ohne-Sonne-Ausschalter scharf wird und wann er ausloest. */
class SunGuardRuleTest {

    private static final SunGuardRule REGEL = new SunGuardRule(1000, 300);

    private static SunGuard aus() {
        return SunGuard.disabled();
    }

    private static SunGuard ein(boolean scharf) {
        return new SunGuard(true, scharf, null);
    }

    @Test
    void tut_nichts_solange_der_waechter_aus_ist() {
        assertEquals(SunGuardAction.NOTHING, REGEL.decide(aus(), OptionalDouble.of(0)));
    }

    @Test
    void wird_scharf_wenn_die_sonne_da_ist() {
        assertEquals(SunGuardAction.ARM, REGEL.decide(ein(false), OptionalDouble.of(1200)));
    }

    @Test
    void wird_nicht_scharf_im_graubereich() {
        // 600 W liegen ueber der Abschalt- und unter der Scharfschwelle: genau der
        // Bereich, in dem eine einzige Schwelle den Waechter flattern liesse.
        assertEquals(SunGuardAction.NOTHING, REGEL.decide(ein(false), OptionalDouble.of(600)));
    }

    @Test
    void loest_aus_wenn_die_sonne_weg_ist() {
        assertEquals(SunGuardAction.TRIP, REGEL.decide(ein(true), OptionalDouble.of(120)));
    }

    @Test
    void loest_nicht_aus_solange_die_sonne_scheint() {
        assertEquals(SunGuardAction.NOTHING, REGEL.decide(ein(true), OptionalDouble.of(2400)));
    }

    @Test
    void loest_nach_dem_abschalten_nicht_erneut_aus() {
        // Stumpf: Wer nachts bewusst einschaltet, soll nicht binnen einer Minute
        // wieder abgewuergt werden.
        SunGuard nachDemAusloesen = new SunGuard(true, false, Instant.parse("2026-09-13T16:15:00Z"));
        assertEquals(SunGuardAction.NOTHING, REGEL.decide(nachDemAusloesen, OptionalDouble.of(0)));
    }

    @Test
    void tut_ohne_messwerte_nichts() {
        // Eine tote Energiequelle sieht aus wie eine Nacht. Ein Waechter, der daraufhin
        // abschaltet, waere schlimmer als keiner.
        assertEquals(SunGuardAction.NOTHING, REGEL.decide(ein(true), OptionalDouble.empty()));
    }

    @Test
    void verlangt_eine_scharfschwelle_ueber_der_abschaltschwelle() {
        assertThrows(IllegalArgumentException.class, () -> new SunGuardRule(300, 300));
        assertThrows(IllegalArgumentException.class, () -> new SunGuardRule(100, 300));
    }
}
