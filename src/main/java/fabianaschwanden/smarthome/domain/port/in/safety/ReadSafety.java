package fabianaschwanden.smarthome.domain.port.in.safety;

import fabianaschwanden.smarthome.domain.model.safety.SmokeDetector;

import java.util.List;

/** Treiber-Port (Use Case): Sicherheitsmelder auslesen (nur lesend). */
public interface ReadSafety {

    List<SmokeDetector> smokeDetectors();
}
