package fabianaschwanden.smarthome.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * JPA-Entity des Tagesvergleichs Prognose gegen Ist – lebt ausschliesslich im
 * Persistence-Adapter. Bedeutung siehe Domänen-Record {@code ForecastAccuracy}.
 *
 * <p>Das Datum ist der Schlüssel: Je Tag gibt es genau einen Eintrag, und «für diesen
 * Tag schon erfasst» ist damit eine Frage an die Datenbank statt an die Anwendung.
 * {@code actualKwh} bleibt {@code null}, solange der Tag nicht abgeschlossen ist –
 * offen und «nichts produziert» dürfen sich nicht gleich anfühlen.
 */
@Entity
@Table(name = "forecast_accuracy")
public class ForecastAccuracyEntity {

    @Id
    @Column(name = "day", nullable = false)
    public LocalDate day;

    @Column(name = "forecast_kwh", nullable = false)
    public double forecastKwh;

    @Column(name = "actual_kwh")
    public Double actualKwh;
}
