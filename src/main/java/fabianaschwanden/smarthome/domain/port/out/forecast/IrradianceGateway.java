package fabianaschwanden.smarthome.domain.port.out.forecast;

import fabianaschwanden.smarthome.domain.model.forecast.IrradianceSeries;

import java.util.Optional;

/**
 * Liefert die Strahlung in Modulebene – Vorhersage für heute/morgen und die gemessene
 * Vergangenheit fürs Lernen.
 *
 * <p>{@code empty} bedeutet „gerade nicht verfügbar", nicht „keine Sonne". Die App hält in
 * dem Fall bewusst die letzte Prognose und macht ihr Alter sichtbar, statt auszufallen
 * (SPEC §6) – der Port wirft deshalb nicht.
 */
public interface IrradianceGateway {

    Optional<IrradianceSeries> fetch();
}
