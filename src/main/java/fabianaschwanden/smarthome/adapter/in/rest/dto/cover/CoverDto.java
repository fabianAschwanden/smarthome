package fabianaschwanden.smarthome.adapter.in.rest.dto.cover;

import fabianaschwanden.smarthome.domain.model.cover.Cover;

/** Transport-Objekt einer Store. {@code position}: 0=zu, 100=offen, -1=unbekannt. */
public record CoverDto(
        String id,
        String name,
        String room,
        int position,
        boolean online,
        String observedAt) {

    public static CoverDto from(Cover c) {
        return new CoverDto(c.id(), c.name(), c.room(), c.position(), c.online(), c.observedAt().toString());
    }
}
