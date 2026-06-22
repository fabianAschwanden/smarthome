package fabianaschwanden.smarthome.domain.model.alert;

/**
 * Einstellungen für Push-Benachrichtigungen bei kritischen Alarmen (z. B. Rauchalarm).
 *
 * <p>{@code enabled}: ob überhaupt gepusht wird. {@code ntfyTopic}: der ntfy.sh-Topic,
 * den die ntfy-App auf dem Handy abonniert. Das Backend sendet einen HTTP-POST an
 * {@code https://ntfy.sh/<topic>}.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record AlertSettings(boolean enabled, String ntfyTopic) {

    public AlertSettings {
        if (ntfyTopic == null) {
            ntfyTopic = "";
        }
        ntfyTopic = ntfyTopic.trim();
    }

    /** Standard: aus, kein Topic. */
    public static AlertSettings disabled() {
        return new AlertSettings(false, "");
    }

    /** Push ist nur möglich, wenn aktiviert UND ein Topic gesetzt ist. */
    public boolean canPush() {
        return enabled && !ntfyTopic.isBlank();
    }
}
