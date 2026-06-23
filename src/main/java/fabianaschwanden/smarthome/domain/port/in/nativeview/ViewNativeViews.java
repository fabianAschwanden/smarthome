package fabianaschwanden.smarthome.domain.port.in.nativeview;

import fabianaschwanden.smarthome.domain.model.nativeview.NativeView;

import java.util.List;

/** Treiber-Port (Use Case): die konfigurierten nativen Geräte-Weboberflächen auflisten. */
public interface ViewNativeViews {

    List<NativeView> list();
}
