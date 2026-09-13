package fabianaschwanden.smarthome.adapter.in.rest.dto.battery;

import fabianaschwanden.smarthome.domain.model.battery.SunGuard;

/**
 * Transport-Objekt des Ohne-Sonne-Ausschalters.
 *
 * <p>{@code armed} steht mit im DTO, weil es den Unterschied zwischen «wartet auf den
 * Sonnenuntergang» und «hat für heute erledigt» erklärt. Ohne diese Angabe sähe ein
 * eingeschalteter Wächter, der nichts tut, kaputt aus.
 */
public record SunGuardDto(boolean enabled, boolean armed, String lastTrippedAt) {

    public static SunGuardDto from(SunGuard guard) {
        return new SunGuardDto(
                guard.enabled(),
                guard.armed(),
                guard.lastTrippedAt() == null ? null : guard.lastTrippedAt().toString());
    }
}
