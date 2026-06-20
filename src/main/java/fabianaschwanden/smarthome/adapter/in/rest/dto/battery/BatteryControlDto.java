package fabianaschwanden.smarthome.adapter.in.rest.dto.battery;

import fabianaschwanden.smarthome.domain.model.battery.BatteryControl;

/** Transport-Objekt des aktuellen Steuerstands. */
public record BatteryControlDto(
        String mode,
        String desiredState,
        String changedAt) {

    public static BatteryControlDto from(BatteryControl control) {
        return new BatteryControlDto(
                control.mode().name(),
                control.desiredState().name(),
                control.changedAt().toString());
    }
}
