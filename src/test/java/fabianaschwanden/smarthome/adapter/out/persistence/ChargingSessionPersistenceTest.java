package fabianaschwanden.smarthome.adapter.out.persistence;

import fabianaschwanden.smarthome.domain.model.charging.ChargingSession;
import fabianaschwanden.smarthome.domain.model.charging.OpenChargingSession;
import fabianaschwanden.smarthome.domain.port.out.charging.ChargingSessionRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ladevorgaenge gegen die echte Datenbank - offene wie abgeschlossene. */
@QuarkusTest
class ChargingSessionPersistenceTest {

    private static final Instant EIN = Instant.parse("2024-05-05T08:00:00Z");
    private static final Instant AUS = EIN.plus(Duration.ofHours(2));

    @Inject
    ChargingSessionRepository repository;

    @Test
    @TestTransaction
    void haelt_den_beginn_fest_und_vervollstaendigt_beim_abschluss() {
        repository.open(EIN);
        assertEquals(EIN, repository.open().orElseThrow().startedAt());
        assertTrue(repository.latest(10).isEmpty(), "ein offener Vorgang zaehlt noch nicht");

        repository.close(new ChargingSession(EIN, AUS, 1800, 3.6, OptionalDouble.of(2000)));

        assertTrue(repository.open().isEmpty());
        ChargingSession gespeichert = repository.latest(10).get(0);
        assertEquals(3.6, gespeichert.energyKwh());
        assertEquals(2000.0, gespeichert.verifiedWatt().getAsDouble());
    }

    @Test
    @TestTransaction
    void oeffnet_keinen_zweiten_vorgang_neben_einem_offenen() {
        // Sonst verkuerzte ein zweites Oeffnen die Dauer des laufenden Vorgangs.
        repository.open(EIN);
        repository.open(EIN.plus(Duration.ofMinutes(30)));

        assertEquals(EIN, repository.open().orElseThrow().startedAt());
    }

    @Test
    @TestTransaction
    void schreibt_den_stand_der_gegenmessung_fort() {
        // Er muss einen Neustart ueberleben - sonst bliebe die Anlage in der Pause
        // ausgeschaltet zurueck.
        repository.open(EIN);
        Instant pausiert = EIN.plus(Duration.ofMinutes(7));

        repository.updateOpen(repository.open().orElseThrow().verifyStarted(pausiert));

        OpenChargingSession offen = repository.open().orElseThrow();
        assertTrue(offen.isPaused());
        assertEquals(pausiert, offen.verifyStartedAt().orElseThrow());

        repository.updateOpen(offen.verifyEnded(pausiert.plusSeconds(90)));
        assertFalse(repository.open().orElseThrow().isPaused());
        assertTrue(repository.open().orElseThrow().verificationDone());
    }

    @Test
    @TestTransaction
    void verwirft_einen_offenen_vorgang_spurlos() {
        repository.open(EIN);

        repository.discardOpen();

        assertTrue(repository.open().isEmpty());
        assertTrue(repository.latest(10).isEmpty());
    }

    @Test
    @TestTransaction
    void liefert_die_juengsten_zuerst() {
        for (int tag = 1; tag <= 3; tag++) {
            Instant start = EIN.plus(Duration.ofDays(tag));
            repository.open(start);
            repository.close(new ChargingSession(
                    start, start.plus(Duration.ofHours(1)), 1000, tag, OptionalDouble.empty()));
        }

        List<ChargingSession> letzte = repository.latest(2);

        assertEquals(2, letzte.size());
        assertEquals(3.0, letzte.get(0).energyKwh());
        assertEquals(2.0, letzte.get(1).energyKwh());
        assertEquals(Optional.empty(), Optional.ofNullable(
                letzte.get(0).verifiedWatt().isPresent() ? 1 : null));
    }
}
