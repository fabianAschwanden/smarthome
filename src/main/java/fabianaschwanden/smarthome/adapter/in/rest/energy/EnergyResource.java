package fabianaschwanden.smarthome.adapter.in.rest.energy;

import fabianaschwanden.smarthome.adapter.in.rest.dto.energy.EnergyHistoryDto;
import fabianaschwanden.smarthome.adapter.in.rest.dto.energy.EnergySnapshotDto;
import fabianaschwanden.smarthome.domain.model.energy.HistoryRange;
import fabianaschwanden.smarthome.domain.port.in.energy.CurrentEnergyQuery;
import fabianaschwanden.smarthome.domain.port.in.energy.EnergyHistoryQuery;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** Treiber-Adapter (REST): aktueller Energie-Schnappschuss und Verlauf (Tag/Woche/Monat). */
@Path("/api/energy")
@Produces(MediaType.APPLICATION_JSON)
public class EnergyResource {

    private final CurrentEnergyQuery currentEnergy;
    private final EnergyHistoryQuery energyHistory;

    public EnergyResource(CurrentEnergyQuery currentEnergy, EnergyHistoryQuery energyHistory) {
        this.currentEnergy = currentEnergy;
        this.energyHistory = energyHistory;
    }

    @GET
    @Path("/current")
    public EnergySnapshotDto current() {
        return EnergySnapshotDto.from(currentEnergy.currentEnergy());
    }

    /** Verlauf von Verbrauch und PV-Produktion als kWh-Zeitreihe. range = day|week|month. */
    @GET
    @Path("/history")
    public EnergyHistoryDto history(@QueryParam("range") @DefaultValue("day") String range) {
        HistoryRange parsed;
        try {
            parsed = HistoryRange.fromParam(range);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("range muss day, week oder month sein");
        }
        return EnergyHistoryDto.from(energyHistory.history(parsed));
    }
}
