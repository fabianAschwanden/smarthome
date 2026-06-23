package fabianaschwanden.smarthome.domain.port.out.nativeview;

import fabianaschwanden.smarthome.domain.model.nativeview.NativeView;

import java.util.List;

/** Getriebener Port: liefert die konfigurierten nativen Weboberflächen. */
public interface NativeViewProvider {

    List<NativeView> views();
}
