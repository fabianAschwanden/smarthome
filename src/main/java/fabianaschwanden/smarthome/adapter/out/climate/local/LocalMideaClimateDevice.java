package fabianaschwanden.smarthome.adapter.out.climate.local;

import fabianaschwanden.smarthome.domain.model.climate.Climate;
import fabianaschwanden.smarthome.domain.model.climate.ClimateMode;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateDevice;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateUnavailable;
import fabianaschwanden.smarthome.support.tuya.TuyaDiscovery;
import fabianaschwanden.smarthome.support.tuya.TuyaSidecarClient;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Echte Midea/NetHome-Plus-Klimaanlage über den lokalen Sidecar (msmart-ng,
 * V3-Authentifizierung mit token/key). Der reine Java-Adapter beherrscht das
 * Midea-LAN-Protokoll nicht – die Steuerung läuft über {@link TuyaSidecarClient}
 * gegen {@code /climate/read} und {@code /climate/control}.
 *
 * <p>Mode-Mapping Domäne ↔ msmart: COOL/HEAT/AUTO direkt, FAN ↔ FAN_ONLY.</p>
 */
public class LocalMideaClimateDevice implements ClimateDevice {

    private static final Logger LOG = Logger.getLogger(LocalMideaClimateDevice.class);

    private final String id;
    private final String name;
    private final String room;
    private final String deviceId;
    private final String token;
    private final String key;
    private final String configuredAddress;
    private final TuyaDiscovery discovery;
    private final TuyaSidecarClient sidecar;

    /**
     * Midea-Geräte erlauben nur EINE LAN-Session und brauchen danach etwas Ruhe. Das
     * UI pollt häufig (alle paar Sekunden) – würde jeder Read das Gerät frisch anfragen,
     * wäre es dauerbesetzt und meldete sich (fälschlich) offline. Darum wird der zuletzt
     * gelesene Zustand kurz gecacht; das Gerät wird höchstens alle {@link #CACHE_TTL_MS}
     * ms neu angefragt. Ein Steuerbefehl invalidiert den Cache sofort.
     */
    private static final long CACHE_TTL_MS = 12_000;

    /**
     * Midea verträgt keine zwei gleichzeitigen LAN-Verbindungen. Read und Control werden
     * darum pro Gerät serialisiert – sonst kollidiert ein Schaltbefehl mit dem laufenden
     * Status-Polling und das Gerät wirft beide Verbindungen ab ("connection reset").
     */
    private final java.util.concurrent.locks.ReentrantLock lock =
            new java.util.concurrent.locks.ReentrantLock();

    private volatile State cachedState;
    private volatile long cachedAt;

    public LocalMideaClimateDevice(
            String id, String name, String room, String deviceId, String token, String key,
            String address, TuyaDiscovery discovery, TuyaSidecarClient sidecar) {
        this.id = id;
        this.name = name;
        this.room = room;
        this.deviceId = deviceId;
        this.token = token;
        this.key = key;
        this.configuredAddress = address;
        this.discovery = discovery;
        this.sidecar = sidecar;
    }

    private String address() {
        // Midea-Geräte tauchen nicht in der Tuya-Discovery auf; configuredAddress ist führend.
        return discovery.ipOf(deviceId).orElse(configuredAddress);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String room() {
        return room;
    }

    @Override
    public void applyPower(boolean on) {
        control(on, null, null);
    }

    @Override
    public void applyMode(ClimateMode mode) {
        control(null, toMsmartMode(mode), null);
    }

    @Override
    public void applyTargetTemp(int temperature) {
        control(null, null, temperature);
    }

    private void control(Boolean power, String mode, Integer target) {
        // Steuerbefehl hat Vorrang: blockierend das Lock holen, damit er nicht mit einem
        // gleichzeitigen Status-Read kollidiert.
        lock.lock();
        try {
            String body = sidecar.controlClimate(deviceId, token, key, address(), power, mode, target)
                    .orElseThrow(() -> new ClimateUnavailable("Klimaanlage '" + name + "' nicht erreichbar"));
            LOG.debugf("[midea] Klima '%s' (%s) gesteuert -> %s", name, id, body);
            // Der Sidecar liefert den Zustand nach dem Befehl gleich mit – als frischen Cache
            // übernehmen, damit der nächste UI-Read den neuen Wert ohne erneuten Geräte-Hit sieht.
            parse(body).ifPresentOrElse(this::cache, this::invalidate);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<State> readState() {
        State cached = cachedState;
        if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
            return Optional.of(cached);
        }
        // Läuft gerade ein Steuerbefehl (Lock belegt), nicht ans Gerät – sonst Kollision.
        // Dann den letzten Cache weitergeben statt zu warten.
        if (!lock.tryLock()) {
            return Optional.ofNullable(cached);
        }
        try {
            Optional<State> fresh = sidecar.readClimate(deviceId, token, key, address()).flatMap(this::parse);
            if (fresh.isPresent()) {
                cache(fresh.get());
                return fresh;
            }
        } finally {
            lock.unlock();
        }
        // Gerät jetzt nicht erreichbar (z. B. Single-Session-Reset): den letzten Cache
        // weitergeben, solange wir einen haben – kein unnötiges Offline-Flackern.
        return Optional.ofNullable(cached);
    }

    /** Parst die flache Sidecar-JSON in einen {@link State}; {@code empty} wenn nicht online. */
    private Optional<State> parse(String json) {
        if (json == null || !Boolean.TRUE.equals(parseBool(json, "online"))) {
            return Optional.empty();
        }
        boolean power = Boolean.TRUE.equals(parseBool(json, "power"));
        ClimateMode mode = fromMsmartMode(parseString(json, "mode"));
        int target = parseTemp(json, "target");
        int current = parseTemp(json, "current");
        int outdoor = parseTemp(json, "outdoor");
        return Optional.of(new State(power, mode, target, current, outdoor));
    }

    private void cache(State state) {
        this.cachedState = state;
        this.cachedAt = System.currentTimeMillis();
    }

    private void invalidate() {
        this.cachedAt = 0;
    }

    // ---- Mode-Mapping ----

    private static String toMsmartMode(ClimateMode mode) {
        return mode == ClimateMode.FAN ? "FAN_ONLY" : mode.name();
    }

    private static ClimateMode fromMsmartMode(String name) {
        if (name == null) {
            return ClimateMode.AUTO;
        }
        return switch (name.toUpperCase()) {
            case "COOL" -> ClimateMode.COOL;
            case "HEAT" -> ClimateMode.HEAT;
            case "FAN_ONLY", "FAN" -> ClimateMode.FAN;
            default -> ClimateMode.AUTO;
        };
    }

    // ---- minimales JSON-Parsing (Sidecar liefert flaches Objekt) ----

    private static Boolean parseBool(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(true|false)").matcher(json);
        return m.find() ? Boolean.valueOf(m.group(1)) : null;
    }

    private static String parseString(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** Liest ein Temperatur-Feld (ganz- oder kommazahlig) als gerundete °C; {@code TEMP_UNKNOWN} wenn fehlt. */
    private static int parseTemp(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)").matcher(json);
        return m.find() ? (int) Math.round(Double.parseDouble(m.group(1))) : Climate.TEMP_UNKNOWN;
    }
}
