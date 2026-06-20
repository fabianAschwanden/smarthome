package fabianaschwanden.smarthome.adapter.in.rest.dto.tuya;

import fabianaschwanden.smarthome.domain.model.tuya.TuyaSwitch;

/** Transport-Objekt eines Schalter-Zustands. {@code critical}: AUS erfordert Bestätigung. */
public record SwitchDto(
        String id,
        String name,
        String room,
        String state,
        boolean online,
        boolean critical,
        String observedAt) {

    public static SwitchDto from(TuyaSwitch sw) {
        return new SwitchDto(
                sw.id(), sw.name(), sw.room(), sw.state().name(), sw.online(), sw.critical(),
                sw.observedAt().toString());
    }
}
