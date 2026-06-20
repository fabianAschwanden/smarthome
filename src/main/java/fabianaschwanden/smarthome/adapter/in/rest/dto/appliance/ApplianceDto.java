package fabianaschwanden.smarthome.adapter.in.rest.dto.appliance;

import fabianaschwanden.smarthome.domain.model.appliance.Appliance;
import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.model.appliance.Temperature;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transport-Objekt einer Anlage; {@code functions} bildet Funktion -> "ON"/"OFF" ab.
 * {@code temperature} ist {@code null} bei Anlagen ohne Heizung.
 */
public record ApplianceDto(
        String id,
        String name,
        String room,
        boolean online,
        String observedAt,
        Map<String, String> functions,
        TemperatureDto temperature) {

    /** Spiegelt das Frontend-Modell ApplianceTemperature. current = -1 -> unbekannt. */
    public record TemperatureDto(int target, int current, int min, int max) {
        static TemperatureDto from(Temperature t) {
            return new TemperatureDto(t.target(), t.current(), t.min(), t.max());
        }
    }

    public static ApplianceDto from(Appliance a) {
        Map<String, String> fns = new LinkedHashMap<>();
        for (Map.Entry<ApplianceFunction, FunctionState> e : a.functions().entrySet()) {
            fns.put(e.getKey().name(), e.getValue().name());
        }
        TemperatureDto temp = a.temperature() == null ? null : TemperatureDto.from(a.temperature());
        return new ApplianceDto(a.id(), a.name(), a.room(), a.online(), a.observedAt().toString(), fns, temp);
    }
}
