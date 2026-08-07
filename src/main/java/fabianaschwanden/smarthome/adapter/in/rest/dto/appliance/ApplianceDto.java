package fabianaschwanden.smarthome.adapter.in.rest.dto.appliance;

import fabianaschwanden.smarthome.domain.model.appliance.Appliance;
import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.model.appliance.Temperature;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

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

    /**
     * Spiegelt das Frontend-Modell ApplianceTemperature. {@code current = -1} bedeutet
     * unbekannt.
     *
     * <p>{@code pending} ist die gewuenschte Soll-Temperatur, solange die Anlage sie noch
     * nicht uebernommen hat - sonst {@code null}. Bewusst ein eigenes Feld und nicht ein
     * ueberschriebener {@code target}: Die Oberflaeche soll den Unterschied zwischen
     * "eingestellt" und "wird gerade gestellt" zeigen koennen.
     */
    public record TemperatureDto(int target, int current, int min, int max, Integer pending) {
        static TemperatureDto from(Temperature t, OptionalInt pending) {
            return new TemperatureDto(
                    t.target(), t.current(), t.min(), t.max(),
                    pending.isPresent() ? pending.getAsInt() : null);
        }
    }

    public static ApplianceDto from(Appliance a) {
        return from(a, OptionalInt.empty());
    }

    public static ApplianceDto from(Appliance a, OptionalInt pendingTarget) {
        Map<String, String> fns = new LinkedHashMap<>();
        for (Map.Entry<ApplianceFunction, FunctionState> e : a.functions().entrySet()) {
            fns.put(e.getKey().name(), e.getValue().name());
        }
        TemperatureDto temp =
                a.temperature() == null ? null : TemperatureDto.from(a.temperature(), pendingTarget);
        return new ApplianceDto(a.id(), a.name(), a.room(), a.online(), a.observedAt().toString(), fns, temp);
    }
}
