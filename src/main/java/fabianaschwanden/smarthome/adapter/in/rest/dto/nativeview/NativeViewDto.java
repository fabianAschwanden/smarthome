package fabianaschwanden.smarthome.adapter.in.rest.dto.nativeview;

import fabianaschwanden.smarthome.domain.model.nativeview.NativeView;

/**
 * REST-DTO einer nativen Weboberfläche. Enthält die Geräte-URL NICHT – stattdessen den
 * relativen Proxy-Pfad ({@code /native/<id>/}), über den das iframe lädt (gleiche Origin,
 * remote-tauglich).
 */
public record NativeViewDto(String id, String name, String icon, String path) {

    public static NativeViewDto from(NativeView v) {
        return new NativeViewDto(v.id(), v.name(), v.icon(), "/native/" + v.id() + "/");
    }
}
