package fabianaschwanden.smarthome.adapter.in.rest.sensor;

import fabianaschwanden.smarthome.adapter.in.rest.dto.sensor.SensorDto;
import fabianaschwanden.smarthome.domain.port.in.sensor.ReadSensors;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** Driving Adapter — liefert die Sensor-Messwerte (nur lesend). */
@Path("/api/sensors")
@Produces(MediaType.APPLICATION_JSON)
public class SensorResource {

    private final ReadSensors sensors;

    public SensorResource(ReadSensors sensors) {
        this.sensors = sensors;
    }

    @GET
    public List<SensorDto> list() {
        return sensors.list().stream().map(SensorDto::from).toList();
    }
}
