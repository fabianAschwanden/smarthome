package fabianaschwanden.smarthome.adapter.in.homekit;

import io.github.hapjava.accessories.SwitchAccessory;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * Attrappe für den Spike (Etappe 1): schaltet nichts, loggt nur. Sie beweist die Kette
 * iPhone → mDNS → HAP-Server → Java-Code, bevor echte Geräte angebunden werden.
 *
 * <p>Wird in Etappe 6 wieder entfernt; die echten Accessories rufen die vorhandenen
 * Driving Ports (PLAN Etappe 3–5).
 */
class DummySwitchAccessory implements SwitchAccessory {

    private static final Logger LOG = Logger.getLogger(DummySwitchAccessory.class);

    private final int id;
    private final String name;
    private volatile boolean on;
    private volatile HomekitCharacteristicChangeCallback callback;

    DummySwitchAccessory(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override public int getId() { return id; }
    @Override public CompletableFuture<String> getName() { return CompletableFuture.completedFuture(name); }
    @Override public CompletableFuture<String> getSerialNumber() { return CompletableFuture.completedFuture("spike-1"); }
    @Override public CompletableFuture<String> getModel() { return CompletableFuture.completedFuture("Dummy"); }
    @Override public CompletableFuture<String> getManufacturer() { return CompletableFuture.completedFuture("smarthome"); }
    @Override public CompletableFuture<String> getFirmwareRevision() { return CompletableFuture.completedFuture("1.0"); }

    @Override
    public void identify() {
        LOG.info("HomeKit: Dummy-Schalter identifizieren");
    }

    @Override
    public CompletableFuture<Boolean> getSwitchState() {
        return CompletableFuture.completedFuture(on);
    }

    @Override
    public CompletableFuture<Void> setSwitchState(boolean state) {
        this.on = state;
        LOG.infof("HomeKit: Dummy-Schalter -> %s", state ? "EIN" : "AUS");
        // Rueckmeldung an alle abonnierten Clients, damit die Home-App den Zustand
        // sofort zeigt, statt bis zum naechsten Poll zu warten.
        HomekitCharacteristicChangeCallback cb = callback;
        if (cb != null) {
            cb.changed();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribeSwitchState(HomekitCharacteristicChangeCallback callback) {
        this.callback = callback;
    }

    @Override
    public void unsubscribeSwitchState() {
        this.callback = null;
    }
}
