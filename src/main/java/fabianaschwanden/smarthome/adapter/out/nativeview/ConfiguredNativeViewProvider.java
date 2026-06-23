package fabianaschwanden.smarthome.adapter.out.nativeview;

import fabianaschwanden.smarthome.domain.model.nativeview.NativeView;
import fabianaschwanden.smarthome.domain.port.out.nativeview.NativeViewProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Adapter: bildet die {@link NativeViewConfig}-Einträge auf {@link NativeView}-Domänen-
 * objekte ab. Die Ziel-URL bleibt in der Config (vom Reverse-Proxy genutzt), nur
 * id/name/icon gehen in die Domäne/ans Frontend.
 */
@ApplicationScoped
public class ConfiguredNativeViewProvider implements NativeViewProvider {

    private final NativeViewConfig config;

    public ConfiguredNativeViewProvider(NativeViewConfig config) {
        this.config = config;
    }

    @Override
    public List<NativeView> views() {
        return config.targets().stream()
                .map(t -> new NativeView(t.id(), t.name(), t.icon()))
                .toList();
    }
}
