package fabianaschwanden.smarthome.adapter.out.appliance.local;

import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.model.appliance.FunctionState;
import fabianaschwanden.smarthome.domain.model.appliance.Temperature;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceDevice;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceUnavailable;
import fabianaschwanden.smarthome.support.tuya.TuyaSidecarClient;
import org.jboss.logging.Logger;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Echte Wellness-Anlage über die Gecko in.touch2-Steuerung (geckolib). Der reine
 * Java-Adapter spricht das Gecko-Protokoll nicht – Lesen/Steuern läuft über den
 * lokalen {@link TuyaSidecarClient} gegen {@code /spa/read} und {@code /spa/control}.
 *
 * <p>Funktions-Mapping (aus der Config): PUMP→{@code pumpKey}, MASSAGE→{@code massageKey},
 * LIGHT→{@code lightKey}, HEATER über die Soll-/Ist-Temperatur (Wasserheizung).</p>
 */
public class LocalGeckoApplianceDevice implements ApplianceDevice {

    private static final Logger LOG = Logger.getLogger(LocalGeckoApplianceDevice.class);

    /** Filterung wird auf den Gecko-WaterCare-Modus abgebildet: EIN/AUS. */
    private static final String WATERCARE_ON = "Standard";
    private static final String WATERCARE_OFF = "Away From Home";

    private final String id;
    private final String name;
    private final String room;
    private final Set<ApplianceFunction> functions;
    private final boolean heated;
    private final int tempMin;
    private final int tempMax;
    private final String ip;
    private final String ident;
    private final String pumpKey;
    private final String massageKey;
    private final String lightKey;
    private final TuyaSidecarClient sidecar;

    /**
     * Gecko-Reads sind langsam (Discovery + Verbindungsaufbau ~30–60 s). Da der
     * Application-Service bei jedem GET synchron {@code readState()} ruft, würde das die
     * REST-Antwort blockieren. Darum: letzter Zustand wird gecacht; {@code readState()}
     * liefert sofort den Cache und stösst bei abgelaufener TTL einen Hintergrund-Refresh
     * an (nie blockierend). Steuerbefehle übernehmen ihre Antwort als frischen Cache.
     */
    private static final long CACHE_TTL_MS = 30_000;
    private volatile State cachedState;
    private volatile long cachedAtMillis;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    public LocalGeckoApplianceDevice(
            String id, String name, String room, Set<ApplianceFunction> functions,
            boolean heated, int tempMin, int tempMax, String ip, String ident,
            String pumpKey, String massageKey, String lightKey,
            TuyaSidecarClient sidecar) {
        this.id = id;
        this.name = name;
        this.room = room;
        this.functions = functions;
        this.heated = heated;
        this.tempMin = tempMin;
        this.tempMax = tempMax;
        this.ip = ip;
        this.ident = ident;
        this.pumpKey = pumpKey;
        this.massageKey = massageKey;
        this.lightKey = lightKey;
        this.sidecar = sidecar;
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
    public Set<ApplianceFunction> functions() {
        return functions;
    }

    @Override
    public boolean heated() {
        return heated;
    }

    @Override
    public void apply(ApplianceFunction function, FunctionState state) {
        boolean on = state == FunctionState.ON;
        String body = switch (function) {
            case PUMP -> sidecar.controlSpa(ip, ident, name, null, pumpKey, null, on).orElse(null);
            case MASSAGE -> sidecar.controlSpa(ip, ident, name, null, massageKey, null, on).orElse(null);
            case LIGHT -> sidecar.controlSpa(ip, ident, name, null, null, lightKey, on).orElse(null);
            // Filterung = Gecko-WaterCare-Modus: EIN -> Standard, AUS -> Away From Home.
            case FILTER -> sidecar.controlSpaWaterCare(ip, ident, name,
                    on ? WATERCARE_ON : WATERCARE_OFF).orElse(null);
            case HEATER -> throw new ApplianceUnavailable(
                    "Heizung wird über die Soll-Temperatur gesteuert, nicht ein/aus");
        };
        if (body == null) {
            throw new ApplianceUnavailable("Anlage '" + name + "' nicht erreichbar");
        }
        LOG.debugf("[gecko] '%s': %s -> %s", name, function, state);
        parse(body).ifPresent(this::cache);  // Antwort enthält den frischen Zustand
    }

    @Override
    public void applyTargetTemp(int target) {
        String body = sidecar.controlSpa(ip, ident, name, target, null, null, null)
                .orElseThrow(() -> new ApplianceUnavailable("Anlage '" + name + "' nicht erreichbar"));
        LOG.debugf("[gecko] '%s': Soll-Temperatur -> %d°C", name, target);
        parse(body).ifPresent(this::cache);
    }

    @Override
    public Optional<State> readState() {
        State cached = cachedState;
        boolean fresh = cached != null && System.currentTimeMillis() - cachedAtMillis < CACHE_TTL_MS;
        if (!fresh) {
            triggerBackgroundRefresh();  // nie blockierend; Cache wird asynchron erneuert
        }
        return Optional.ofNullable(cached);  // sofort: letzter Stand (oder leer beim Erststart)
    }

    /** Liest das Spa im Hintergrund (höchstens ein Refresh gleichzeitig) und füllt den Cache. */
    private void triggerBackgroundRefresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        Thread.ofVirtual().name("gecko-refresh-" + id).start(() -> {
            try {
                sidecar.readSpa(ip, ident, name).flatMap(this::parse).ifPresent(this::cache);
            } finally {
                refreshing.set(false);
            }
        });
    }

    private void cache(State state) {
        this.cachedState = state;
        this.cachedAtMillis = System.currentTimeMillis();
    }

    /** Parst die flache Sidecar-JSON in einen {@link State}; {@code empty} wenn nicht online. */
    private Optional<State> parse(String json) {
        if (json == null || !Boolean.TRUE.equals(parseBool(json, "online"))) {
            return Optional.empty();
        }
        Map<ApplianceFunction, FunctionState> states = new EnumMap<>(ApplianceFunction.class);
        if (functions.contains(ApplianceFunction.PUMP)) {
            states.put(ApplianceFunction.PUMP, fromBool(parseKeyBool(json, "pumps", pumpKey)));
        }
        if (functions.contains(ApplianceFunction.MASSAGE)) {
            states.put(ApplianceFunction.MASSAGE, fromBool(parseKeyBool(json, "pumps", massageKey)));
        }
        if (functions.contains(ApplianceFunction.FILTER)) {
            // Filterung = WaterCare-Modus: alles ausser "Away From Home" gilt als EIN.
            String wc = parseString(json, "watercare");
            boolean filterOn = wc != null && !WATERCARE_OFF.equalsIgnoreCase(wc);
            states.put(ApplianceFunction.FILTER, fromBool(filterOn));
        }
        if (functions.contains(ApplianceFunction.LIGHT)) {
            states.put(ApplianceFunction.LIGHT, fromBool(parseKeyBool(json, "lights", lightKey)));
        }
        if (functions.contains(ApplianceFunction.HEATER)) {
            states.put(ApplianceFunction.HEATER, FunctionState.ON); // Heizung dauerhaft aktiv (Thermostat)
        }

        Temperature temp = null;
        if (heated) {
            int target = round(parseNumber(json, "target"), tempMin);
            int current = round(parseNumber(json, "current"), Temperature.UNKNOWN);
            // Soll-/Ist-Werte des Geräts so übernehmen wie gemeldet; die Min/Max sind nur
            // der Steuerbereich, nicht die Schranke für angezeigte Ist-Gerätewerte. Damit
            // die Temperature-Invariante (target in [min,max]) hält, wird min/max bei Bedarf
            // auf den gemeldeten Wert geweitet.
            int min = Math.min(tempMin, target);
            int max = Math.max(tempMax, target);
            temp = new Temperature(target, current, min, max);
        }
        return Optional.of(new State(states, temp));
    }

    private static FunctionState fromBool(Boolean b) {
        return Boolean.TRUE.equals(b) ? FunctionState.ON : FunctionState.OFF;
    }

    private static int round(Double v, int fallback) {
        return v == null ? fallback : (int) Math.round(v);
    }

    // ---- minimales JSON-Parsing (flacher Sidecar-Output) ----

    private static Boolean parseBool(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(true|false)").matcher(json);
        return m.find() ? Boolean.valueOf(m.group(1)) : null;
    }

    private static Double parseNumber(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)").matcher(json);
        return m.find() ? Double.valueOf(m.group(1)) : null;
    }

    private static String parseString(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** Liest {@code "<group>": { ... "<key>": true|false ... }} (z. B. pumps/lights). */
    private static Boolean parseKeyBool(String json, String group, String key) {
        if (key == null) {
            return null;
        }
        Matcher g = Pattern.compile("\"" + group + "\"\\s*:\\s*\\{([^}]*)\\}").matcher(json);
        if (!g.find()) {
            return null;
        }
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)").matcher(g.group(1));
        return m.find() ? Boolean.valueOf(m.group(1)) : null;
    }
}
