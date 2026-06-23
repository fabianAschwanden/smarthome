package fabianaschwanden.smarthome.application.service.nativeview;

import fabianaschwanden.smarthome.domain.model.nativeview.NativeView;
import fabianaschwanden.smarthome.domain.port.in.nativeview.ViewNativeViews;
import fabianaschwanden.smarthome.domain.port.out.nativeview.NativeViewProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Application-Service: liefert die konfigurierten nativen Weboberflächen (Metadaten).
 * Das Einbetten/Proxyen selbst macht der {@code NativeProxy}-Adapter.
 */
@ApplicationScoped
public class NativeViewService implements ViewNativeViews {

    private final NativeViewProvider provider;

    public NativeViewService(NativeViewProvider provider) {
        this.provider = provider;
    }

    @Override
    public List<NativeView> list() {
        return provider.views();
    }
}
