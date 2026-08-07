package fabianaschwanden.smarthome.application.service.forecast;

import fabianaschwanden.smarthome.domain.model.battery.RelayState;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatterySchedule;
import fabianaschwanden.smarthome.domain.model.batteryschedule.BatteryScheduleType;
import fabianaschwanden.smarthome.domain.model.forecast.ChargeRecommendation;
import fabianaschwanden.smarthome.domain.model.forecast.Confidence;
import fabianaschwanden.smarthome.domain.model.forecast.ConsumptionBaseline;
import fabianaschwanden.smarthome.domain.model.forecast.SurplusWindow;
import fabianaschwanden.smarthome.domain.port.in.batteryschedule.ManageBatterySchedules;
import fabianaschwanden.smarthome.domain.port.in.forecast.NoRecommendationAvailable;
import fabianaschwanden.smarthome.domain.port.in.forecast.SurplusQuery;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Übernahme der Empfehlung als Batterie-Zeitplan. Wichtigster Punkt: Dieser Dienst
 * schaltet nichts selbst, er delegiert an Use Case 14.
 */
@QuarkusTest
class ApplyRecommendationServiceTest {

    private static final Instant FROM = Instant.parse("2026-08-04T09:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-04T13:00:00Z");

    /** Keine anstehende Wellness-Heizung - der Regelfall fuer diese Tests. */
    private static final class FakeWellnessSchedules
            implements fabianaschwanden.smarthome.domain.port.in.applianceschedule.ManageApplianceSchedules {

        @Override
        public java.util.List<fabianaschwanden.smarthome.domain.model.applianceschedule.ApplianceSchedule> all() {
            return java.util.List.of();
        }

        @Override
        public fabianaschwanden.smarthome.domain.model.applianceschedule.ApplianceSchedule save(
                fabianaschwanden.smarthome.domain.model.applianceschedule.ApplianceSchedule schedule) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(java.util.UUID id) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeSurplus implements SurplusQuery {
        Optional<ChargeRecommendation> recommendation = Optional.empty();

        @Override public List<SurplusWindow> windows() { return List.of(); }
        @Override public Optional<ChargeRecommendation> recommendation() { return recommendation; }
        @Override public Optional<ConsumptionBaseline> baseline() { return Optional.empty(); }
    }

    private static final class FakeSchedules implements ManageBatterySchedules {
        final List<BatterySchedule> saved = new ArrayList<>();

        @Override public List<BatterySchedule> all() { return List.copyOf(saved); }
        @Override public BatterySchedule save(BatterySchedule schedule) {
            saved.add(schedule);
            return schedule;
        }
        @Override public BatterySchedule setEnabled(UUID id, boolean enabled) {
            throw new UnsupportedOperationException();
        }
        @Override public void delete(UUID id) {
            throw new UnsupportedOperationException();
        }
    }

    private final FakeSurplus surplus = new FakeSurplus();
    private final FakeSchedules schedules = new FakeSchedules();
    private final ApplyRecommendationService service =
            new ApplyRecommendationService(surplus, schedules, new FakeWellnessSchedules());

    private void empfehlungLiegtVor() {
        surplus.recommendation = Optional.of(new ChargeRecommendation(
                new SurplusWindow(FROM, TO, 6.4, 2100), Confidence.LEARNED));
    }

    @Test
    void legtEinenCountdownAufDenFensterbeginn() {
        empfehlungLiegtVor();

        BatterySchedule schedule = service.apply();

        assertEquals(1, schedules.saved.size(), "genau ein Zeitplan");
        assertEquals(BatteryScheduleType.COUNTDOWN, schedule.type(),
                "die Empfehlung gilt fuer dieses eine Fenster, nicht als Regel");
        assertEquals(RelayState.ON, schedule.action());
        assertEquals(FROM, schedule.fireAt());
        assertTrue(schedule.enabled());
    }

    @Test
    void ohneEmpfehlungGibtEsEinenFachfehler() {
        // Wer "uebernehmen" drueckt, erwartet einen Zeitplan - ein stilles Nichts waere
        // vom Erfolg nicht zu unterscheiden. REST bildet das auf 409 ab.
        assertThrows(NoRecommendationAvailable.class, service::apply);
        assertTrue(schedules.saved.isEmpty());
    }

    @Test
    void schaltetNichtSelbstSondernDelegiert() {
        // Der Dienst kennt nur den UC-14-Port; er hat keinen eigenen Schaltpfad.
        empfehlungLiegtVor();

        service.apply();

        assertEquals(1, schedules.saved.size());
        assertEquals(BatteryScheduleType.COUNTDOWN, schedules.saved.get(0).type());
    }

    @Test
    void jederAufrufErzeugtEinenEigenenZeitplan() {
        empfehlungLiegtVor();

        BatterySchedule erster = service.apply();
        BatterySchedule zweiter = service.apply();

        assertEquals(2, schedules.saved.size());
        assertTrue(!erster.id().equals(zweiter.id()), "eigene IDs");
    }
}
