package fabianaschwanden.smarthome.adapter.in.rest.dto.alert;

import fabianaschwanden.smarthome.domain.model.alert.AlertSettings;

/** Transport-Objekt der Alert-Einstellungen. */
public record AlertSettingsDto(boolean enabled, String ntfyTopic) {

    public static AlertSettingsDto from(AlertSettings s) {
        return new AlertSettingsDto(s.enabled(), s.ntfyTopic());
    }

    public AlertSettings toDomain() {
        return new AlertSettings(enabled, ntfyTopic == null ? "" : ntfyTopic);
    }
}
