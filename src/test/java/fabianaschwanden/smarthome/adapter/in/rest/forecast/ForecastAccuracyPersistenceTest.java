package fabianaschwanden.smarthome.adapter.in.rest.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.AutoApplyState;
import fabianaschwanden.smarthome.domain.model.forecast.ForecastAccuracy;
import fabianaschwanden.smarthome.domain.port.out.forecast.AutoApplyStateRepository;
import fabianaschwanden.smarthome.domain.port.out.forecast.ForecastAccuracyRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schreiben und Lesen gegen die echte Datenbank.
 *
 * <p>Die Attrappen in den Service-Tests konnten einen Defekt nicht sehen, der nur beim
 * ersten INSERT auftrat: Wurde die Entity vor dem Fuellen persistiert, schrieb Hibernate
 * eine Zeile voller NULL-Werte und scheiterte an den NOT-NULL-Spalten.
 *
 * <p>{@code @TestTransaction} ist hier noetig, nicht bequem: Ohne Transaktion haelt
 * {@code @QuarkusTest} den Request-Kontext - und damit dieselbe Hibernate-Session - ueber
 * die ganze Methode offen. Ein zweites findById lieferte dann die bereits geladene Entity
 * aus dem Persistence-Kontext und saehe die zwischenzeitliche Aenderung nicht. Nebenbei
 * raeumt der Rollback hinter jedem Test auf.
 */
@QuarkusTest
class ForecastAccuracyPersistenceTest {

    @Inject
    ForecastAccuracyRepository accuracy;

    @Inject
    AutoApplyStateRepository autoApply;

    @Test
    @TestTransaction
    void legt_einen_neuen_tag_an_und_traegt_den_ist_wert_nach() {
        LocalDate tag = LocalDate.of(2026, 1, 15);

        accuracy.save(ForecastAccuracy.predicted(tag, 12.5));
        assertEquals(12.5, accuracy.byDate(tag).orElseThrow().forecastKwh());
        assertTrue(accuracy.byDate(tag).orElseThrow().actualKwh().isEmpty());

        accuracy.save(accuracy.byDate(tag).orElseThrow().settledWith(11.0));
        assertEquals(11.0, accuracy.byDate(tag).orElseThrow().actualKwh().getAsDouble());
    }

    @Test
    @TestTransaction
    void legt_den_zustand_der_automatik_an_und_schreibt_ihn_fort() {
        autoApply.save(AutoApplyState.disabled().withEnabled(true));
        assertTrue(autoApply.load().enabled());

        autoApply.save(autoApply.load().withEnabled(false));
        assertEquals(false, autoApply.load().enabled());
    }
}
