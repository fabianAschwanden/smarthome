package fabianaschwanden.smarthome.domain.model.forecast;

import java.time.Instant;

/**
 * Das Zeitfenster, in dem die Sonne so auf die Fassade drückt, dass Beschatten sich
 * lohnt: von {@code from} bis {@code to} (exklusiv), mit der stärksten erwarteten
 * Einstrahlung {@code peakGti} und der Innentemperatur, die zur Empfehlung geführt hat.
 *
 * <p>Beschattet wird nicht wegen der Sonne allein: Ein strahlender Wintertag heizt das
 * Haus nicht auf. Erst die Kombination aus starker Einstrahlung <em>und</em> ohnehin
 * warmen Räumen macht geschlossene Storen sinnvoll – sonst nähme man dem Wohnzimmer
 * grundlos das Licht.
 *
 * <p>Value Object: immutable {@code record}.
 */
public record HeatProtectionWindow(Instant from, Instant to, double peakGti, double indoorTemp) {

    public HeatProtectionWindow {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from und to dürfen nicht null sein");
        }
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("to muss nach from liegen: " + from + " → " + to);
        }
        if (peakGti < 0) {
            throw new IllegalArgumentException("peakGti darf nicht negativ sein: " + peakGti);
        }
    }
}
