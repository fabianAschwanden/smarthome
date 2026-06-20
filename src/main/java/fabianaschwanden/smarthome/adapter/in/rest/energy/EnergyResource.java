package fabianaschwanden.smarthome.adapter.in.rest.energy;

import fabianaschwanden.smarthome.adapter.in.rest.dto.energy.EnergySnapshotDto;
import fabianaschwanden.smarthome.domain.port.in.energy.CurrentEnergyQuery;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Treiber-Adapter (REST): stellt den aktuellen Energie-Schnappschuss bereit. */
@Path("/api/energy")
@Produces(MediaType.APPLICATION_JSON)
public class EnergyResource {

    private final CurrentEnergyQuery currentEnergy;

    public EnergyResource(CurrentEnergyQuery currentEnergy) {
        this.currentEnergy = currentEnergy;
    }

    @GET
    @Path("/current")
    public EnergySnapshotDto current() {
        return EnergySnapshotDto.from(currentEnergy.currentEnergy());
    }
}
